-- Re-grade learning tiers from authored prerequisite reachability. Tree depth is
-- deliberately absent: being nested under a topic says where content belongs,
-- not how advanced the capability is.

WITH RECURSIVE direct_edge AS (
    SELECT n.node_id,
           CASE WHEN coalesce(p->>'nodeId', p->>'node_id') ~
                     '^[0-9a-fA-F-]{36}$'
                THEN coalesce(p->>'nodeId', p->>'node_id')::uuid END prerequisite_id
    FROM skill_nodes n
    CROSS JOIN LATERAL jsonb_array_elements(coalesce(n.prerequisite, '[]'::jsonb)) p
), walk(node_id, prerequisite_id, hops, visited) AS (
    SELECT node_id, prerequisite_id, 1, ARRAY[node_id, prerequisite_id]
    FROM direct_edge WHERE prerequisite_id IS NOT NULL
    UNION ALL
    SELECT w.node_id, e.prerequisite_id, w.hops + 1,
           w.visited || e.prerequisite_id
    FROM walk w
    JOIN direct_edge e ON e.node_id = w.prerequisite_id
    WHERE e.prerequisite_id IS NOT NULL
      AND NOT e.prerequisite_id = ANY(w.visited)
), dependency_depth AS (
    SELECT n.node_id, coalesce(max(w.hops), 0) dependency_hops
    FROM skill_nodes n LEFT JOIN walk w ON w.node_id = n.node_id
    GROUP BY n.node_id
), ranked AS (
    SELECT n.node_id,
           ntile(3) OVER (
               PARTITION BY n.parent_node
               ORDER BY d.dependency_hops, coalesce(n.sort_order, 2147483647), n.node_id
           ) AS dependency_tier
    FROM skill_nodes n JOIN dependency_depth d ON d.node_id = n.node_id
    WHERE n.semantic_type IN ('SKILL', 'CAPABILITY', 'TOPIC', 'CHECKPOINT')
)
UPDATE skill_nodes n SET tier = r.dependency_tier
FROM ranked r WHERE r.node_id = n.node_id;

-- A measurable node's evidence bar follows the capability tier. Topic and
-- checkpoint completion is aggregated from children, so it has no own bar.
UPDATE skill_nodes SET required_proficiency = CASE tier
    WHEN 1 THEN 55 WHEN 2 THEN 70 WHEN 3 THEN 85 ELSE NULL END
WHERE semantic_type IN ('SKILL', 'CAPABILITY');

UPDATE skill_nodes SET required_proficiency = NULL
WHERE semantic_type IN ('TOPIC', 'CHECKPOINT');

-- A container opens when its earliest child does. It must not become an
-- advanced gate merely because the container itself appears late on a parent spine.
UPDATE skill_nodes topic SET tier = child.first_tier
FROM (
    SELECT parent_node, min(tier) first_tier
    FROM skill_nodes WHERE parent_node IS NOT NULL
    GROUP BY parent_node
) child
WHERE topic.node_id = child.parent_node
  AND topic.semantic_type IN ('TOPIC', 'CHECKPOINT');
