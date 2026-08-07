-- Correct databases that applied V20260807_02 before its container rule existed.
UPDATE skill_nodes topic SET tier = child.first_tier
FROM (
    SELECT parent_node, min(tier) first_tier
    FROM skill_nodes WHERE parent_node IS NOT NULL
    GROUP BY parent_node
) child
WHERE topic.node_id = child.parent_node
  AND topic.semantic_type IN ('TOPIC', 'CHECKPOINT');
