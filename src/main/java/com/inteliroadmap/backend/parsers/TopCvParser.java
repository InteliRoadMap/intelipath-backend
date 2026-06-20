package com.inteliroadmap.backend.parsers;

import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.engines.CurlEngine;
import com.inteliroadmap.backend.exceptions.BlockedIpException;
import com.inteliroadmap.backend.exceptions.ParsingException;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TopCvParser {

    @Value("${scraper.thread-sleep}")
    private int THREAD_SLEEP;

    @Value("${scraper.topcv.url}")
    private String TOPCV_TARGET;

    private final RecruitmentPostRepository recruitmentPostRepository;
    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;

    private final DateTimeFormatter formatDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void parseTopCvJobs() {
        try {
            int totalPage = 1, page = 1, count = 0;
            boolean run;
            do {
                String url = TOPCV_TARGET + "?sort=new&page=" + page;
                log.info("ScraperService: Parsing TopCv Jobs {}", url);
                Document doc = CurlEngine.getDocument(url);

                run = page <= totalPage;

                if (doc != null) {
                    Element paginateText = doc.selectFirst("#job-listing-paginate-text");

                    if (paginateText == null) {
                        log.warn("Could not find pagination text. The page layout may have changed or Cloudflare blocked the request.");
                        break;
                    }
                    System.out.println(paginateText.text());

                    Pattern pattern = Pattern.compile("/\\s*(\\d+)");
                    Matcher m = pattern.matcher(paginateText.text());
                    if (m.find()) totalPage = Integer.parseInt(m.group(1));
                    log.info("Current page: {}/{}", page, totalPage);

                    Elements jobs = doc.select(".job-item-search-result");
                    log.info("Found {} jobs on page {}. Starting scrape...", jobs.size(), page);

                    for (Element job : jobs) {
                        if (count >= 20) { //Litmit total JOB Craws
                            log.info("Reached scraper delimiter. Stopping scrape.");
                            run = false;
                            break;
                        }
                        try {
                            Thread.sleep(THREAD_SLEEP + RandomUtils.nextInt(0, 1000));

                            count++;
                            log.info("Scraping job No. {}...", count);

                            String companyLink = job.select(".title-block").select(".company").attr("href");
                            String companyId = "";
                            Matcher matcher = Pattern.compile("(\\d+)").matcher(companyLink);
                            if (matcher.find()) {
                                companyId = "topcv.co" + matcher.group(1);
                            }
                            log.info("Co. ID: {}", companyId);
                            Company company = getCompanyDetail(companyLink, companyId);

                            String recruitmentLink = job.select(".title-block").select(".title").select("a").attr("href");
                            String recruitmentId = "topcv.rec" + job.attr("data-job-id");
                            log.info("Rec ID: {}", recruitmentId);
                            Recruitment recruitment = getRecruitmentDetail(recruitmentLink, recruitmentId);

                            if (company != null && recruitment != null) {
                                RecruitmentPost post = RecruitmentPost.builder()
                                        .company(company)
                                        .recruitment(recruitment)
                                        .build();
                                recruitmentPostRepository.save(post);
                            }

                        } catch (BlockedIpException e) {
                            throw e;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ParsingException("Interrupted", e);
                        } catch (Exception e) {
                            log.error("Error occurred while scraping a job. Continuing to next.", e);
                        }
                    }
                }
                page++;
            } while (run);
        } catch (Exception e) {
            log.error("Error setting up parser", e);
        }
    }

    private String convertToEng(String type){
        type = type.trim();
        if(type.contains("Năm thành lập")) return "Year Of Establishment";
        if(type.contains("Quy mô")) return "Scale";
        if(type.contains("Độ tuổi trung bình")) return "Average Age";
        if(type.contains("Lĩnh vực hoạt động")) return "Field Of Activity";
        if(type.contains("Lĩnh vực chính")) return "Main Field";
        if(type.contains("Yêu cầu")) return "Requirements";
        if(type.contains("Quyền lợi")) return "Benefits";
        if(type.contains("Chuyên môn")) return "Specialize";
        if(type.contains("Mô tả")) return "Job Description";
        if(type.contains("Địa điểm")) return "Location";
        if(type.contains("Thời gian")) return "Work Time";
        if(type.contains("Cấp bậc")) return "Rank";
        if(type.contains("Học vấn")) return "Education";
        if(type.contains("Số lượng")) return "Hiring";
        if(type.contains("Hình thức")) return "Form of work";
        if(type.contains("Nghề liên quan")) return "Related Jobs";
        if(type.contains("Kỹ năng cần có")) return "Skills required";
        if(type.contains("Kỹ năng nên có")) return "Should-have Skills";
        if(type.contains("Loại hình")) return "Type of work";
        if(type.contains("theo khu vực")) return "Find Jobs By Region";
        if(type.contains("địa giới trước sáp nhập")) return "Find Jobs By Pre-merger Boundary";
        if(type.contains("địa giới sau sáp nhập")) return "Find Jobs By Post-merger Boundary";
        return type;
    }

    private final Map<String, String> normalCustom = Map.of(
            "jobTitle",".job-detail__info--title",
            "jobBasicInfos",".job-detail__info--section",
            "applicationDeadline",".job-detail__info--deadline-date",
            "jobTags",".job-tags__group",
            "jobDescription",".job-description__item",
            "jobGeneralInfo",".box-general-group",
            "jobRelatedTags",".box-category"
    );

    private final Map<String, String> premiumCustom = Map.of(
            "jobTitle",".premium-job-basic-information__content--title",
            "jobBasicInfos",".basic-information-item",
            "applicationDeadline",".job-detail__info--deadline-date",
            "jobTags",".job-tags__group",
            "jobDescription",".premium-job-description__box",
            "jobGeneralInfo",".premium-job-general-information__content--row",
            "jobRelatedTags",".premium-job-related-tags__section"
    );

    private List<String> getDescriptions(Elements items, List<String> list){
        if(list == null) list = new ArrayList<>();
        items.select("br").append("\\n");
        for(Element e : items){
            if(e.children().size() > 1 && !(e.children().select("br").size() > 1)) {
                getDescriptions(e.children(), list);
            }
            else {
                if(e.text().isEmpty()) continue;
                String line = e.text().replace("\\n", "\n");
                list.add(line);
            }
        }
        return list;
    }

    public Company getCompanyDetail(String companyLink, String companyId) {
        Document doc = CurlEngine.getDocument(companyLink);
        Company company = null;

        if(doc != null) {
            if(companyRepository.existsByTopCvCompanyId(companyId)) {
                log.info("Co. ID existed. Skipping...");
                company = companyRepository.findByTopCvCompanyId(companyId);
                return company;
            }

            String companyLogo = doc.select(".company-logo").select("img").attr("src");
            String companyName = doc.select(".company-name").text();

            Elements introContents;
            introContents = doc.select(".intro-content");
            if (introContents.isEmpty()) {
                introContents = doc.select(".company-info").select(".content");
            }
            List<String> companyIntro = getDescriptions(introContents, null);

            Map<String, String> companyInfo = new LinkedHashMap<>();
            Elements infos = doc.select(".box-info-company-general-item__info");
            for (Element info : infos) {
                String infoTitle = convertToEng(info.select(".box-info-company-general-item__info--title").text());
                if(infoTitle.equals("Mã số thuế")) {continue;}

                String infoValue = info.select(".box-info-company-general-item__info--desc").text();
                companyInfo.put(infoTitle, infoValue);
            }

            Elements contacts;
            contacts = doc.select(".info-line");
            if (contacts.isEmpty()) {
                contacts = doc.select(".company-info").select(".box-body__address");
            }
            List<String> companyContact = getDescriptions(contacts, null);

            company = Company.builder()
                    .topCvCompanyId(companyId)
                    .companyLink(companyLink)
                    .logo(companyLogo)
                    .name(companyName)
                    .introduction(companyIntro)
                    .info(companyInfo)
                    .contact(companyContact)
                    .build();

            company = companyRepository.save(company);
        }
        return  company;
    }

    public Recruitment getRecruitmentDetail(String recruitmentLink, String recruitmentId) {
        Document doc = CurlEngine.getDocument(recruitmentLink);
        Recruitment recruitment = null;
        boolean premium = false;

        if (doc != null) {
            Element jobInfo = null;

            if(recruitmentRepository.existsByTopCvRecruitmentId(recruitmentId)) {
                log.info("Rec ID existed. Skipping...");
                recruitment = recruitmentRepository.findByTopCvRecruitmentId(recruitmentId);
                return recruitment;
            }

            jobInfo = doc.selectFirst(".job-detail");
            if (jobInfo == null) {
                // Normal custom tags not found
                // Proceed to get Premium custom tags
                jobInfo = doc.selectFirst(".premium-job-detail");
                premium = true;

//                if (jobInfo == null){
//                    // Premium custom tags not found
//                    // Proceed to get Simple custom tags
//                    jobInfo = doc.selectFirst(".section-content-job-detail");
//                    simple = true;
//                }
            }

            if (jobInfo == null) {
                throw new NullPointerException("Parsing format not yet implemented for " + recruitmentLink);
            }

            Map<String, String> jobDetail;

//            if (simple) jobDetail = simpleCustom;
            if (premium) jobDetail = premiumCustom;
            else jobDetail = normalCustom;

            // -------------------------- Normal & Premium Custom Tags -------------------------
            String jobTitle = jobDetail.get("jobTitle");
            String jobBasicInfos = jobDetail.get("jobBasicInfos");
            String jobDeadline = jobDetail.get("applicationDeadline");
            String jobTags = jobDetail.get("jobTags");
            String jobDescriptions = jobDetail.get("jobDescription");
            String jobGeneralInfos = jobDetail.get("jobGeneralInfo");
            String jobRelatedTags = jobDetail.get("jobRelatedTags");

            // ----------------------------------- JOB HEADER ----------------------------------
            String recruitmentTitle = jobInfo.select(jobTitle).text();
            String recruitmentSalary = jobInfo.select(jobBasicInfos).getFirst().text();
            String recruitmentLocation = jobInfo.select(jobBasicInfos).get(1).text();
            String recruitmentExperience = jobInfo.select(jobBasicInfos).getLast().text();

            // Get application deadline date
            LocalDate recruitmentDeadline = LocalDate.parse(
                    jobInfo.select(jobDeadline).text(),
                    formatDate
            );

            // ------------------------------------ JOB TAGS -----------------------------------
            Map<String, List<String>> recruitmentTag = new LinkedHashMap<>();
            Elements tags = jobInfo.select(jobTags);
            for (Element tag : tags) {
                String tagName = tag.child(0).text();
                Elements tagItems = tag.child(1).child(0).children();

                List<String> tagList = new ArrayList<>();
                for (Element tagItem : tagItems) {
                    tagList.add(tagItem.text());
                }
                recruitmentTag.put(convertToEng(tagName), tagList);
            }

            // -------------------------------- JOB DESCRIPTIONS -------------------------------
            Map<String, List<String>> recruitmentDescription = new LinkedHashMap<>();
            Elements descriptions = jobInfo.select(jobDescriptions);

            // Remove job application area
            descriptions.removeLast();

            for(Element desc : descriptions) {
                String descName = convertToEng(desc.child(0).text());
                Element descText = desc.child(1);

                List<String> descLine = getDescriptions(descText.children(), null);
                recruitmentDescription.put(descName, descLine);
            }

            // ------------------------------- JOB GENERAL INFOS -------------------------------
            Map<String, String> recruitmentGeneralInfo = new LinkedHashMap<>();
            Elements generalInfos = jobInfo.select(jobGeneralInfos);

            for(Element info : generalInfos) {
                String headLine = null;
                int i = 0;

                for (Element child : info.child(1).children()) {
                    String line = child.text();
                    if(i++ % 2 == 0) {
                        headLine = convertToEng(child.text());
                    } else {
                        recruitmentGeneralInfo.put(headLine, line);
                    }
                }
            }

            // -------------------------------- JOB RELATED TAGS -------------------------------
            Map<String, List<String>> recruitmentRelatedTag = new LinkedHashMap<>();
            Elements relatedTags = jobInfo.select(jobRelatedTags);
            for(Element relatedTag : relatedTags) {
                String tagName = convertToEng(relatedTag.child(0).text());

                Elements tagList = relatedTag.child(1).children();
                int i = 0;
                List<String> tagItem = new ArrayList<>();
                for (Element tag : tagList) {
                    tagItem.add(tag.text());
                }
                recruitmentRelatedTag.put(tagName, tagItem);
            }

            recruitment = Recruitment.builder()
                    .topCvRecruitmentId(recruitmentId)          // String
                    .recruitmentLink(recruitmentLink)           // String
                    .title(recruitmentTitle)                    // String
                    .salary(recruitmentSalary)                  // String
                    .location(recruitmentLocation)              // String
                    .experience(recruitmentExperience)          // String
                    .applicationDeadline(recruitmentDeadline)   // LocalDate
                    .tags(recruitmentTag)                       // Map<String, List<String>>
                    .descriptions(recruitmentDescription)       // Map<String, List<String>>
                    .generalInfos(recruitmentGeneralInfo)       // Map<String, String>
                    .relatedTags(recruitmentRelatedTag)         // Map<String, List<String>>
                    .build();

            recruitment = recruitmentRepository.save(recruitment);
        }
        return recruitment;
    }
}
