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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
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
        // -------------------------- LIMITER -------------------------- //
        int limiter = 20;               // Set this value to 0 or below to remove limiter
        boolean limitOn = limiter > 0;  // Turn on limiter if its value is > 0
        // -------------------------- LIMITER -------------------------- //

        try {
            int totalPage = 1, currentPage = 1, count = 0;
            boolean run;
            do {
                String url = TOPCV_TARGET + "?sort=new&page=" + currentPage;
                log.info("ScraperService: Parsing TopCv Jobs {}", url);
                Document doc = CurlEngine.getDocument(url);

                run = currentPage <= totalPage; // Continue scraping next page if available

                if (doc != null) {
                    Element paginateText = doc.selectFirst("#job-listing-paginate-text");

                    if (paginateText == null) {
                        log.warn("Could not find pagination text. The page layout may have changed or Cloudflare blocked the request.");
                        break;
                    }

                    Matcher m = Pattern.compile("/\\s*(\\d+)").matcher(paginateText.text());
                    if (m.find()) totalPage = Integer.parseInt(m.group(1));
                    log.info("Current page: {}/{}", currentPage, totalPage);

                    Elements jobs = doc.select(".job-item-search-result");
                    log.info("Found {} jobs on page {}. Starting scrape...", jobs.size(), currentPage);

                    for (Element job : jobs) {
                        // -------------------------- LIMITER -------------------------- //
                        if (limitOn && count >= limiter) {
                            log.info("Reached Scraping Limiter. Stopping scrape.");
                            run = false;
                            break;
                        }
                        // -------------------------- LIMITER -------------------------- //
                        try {
                            // ------------------------ THREAD SLEEP ----------------------- //
                            // Thread Sleep to prevent expected IP Blocked by CloudFlare due to abnormal activities
                            Thread.sleep(THREAD_SLEEP + RandomUtils.nextInt(0, 2000));
                            // ------------------------ THREAD SLEEP ----------------------- //

                            count++;
                            log.info("Scraping job No. {}...", count);

                            // Get Company Link
                            String companyLink = job.select(".title-block").select(".company").attr("href");
                            String companyId = "";
                            Matcher matcher = Pattern.compile("(?:/|id=)(\\d+)(?:\\.html|&|$)").matcher(companyLink);
                            if (matcher.find()) {
                                companyId = "topcv.co" + matcher.group(1);
                            }
                            log.info("Co. ID: {}", companyId);
                            Company company = getCompanyDetail(companyLink, companyId);

                            // Get Recruitment Link
                            String recruitmentLink = job.select(".title-block").select(".title").select("a").attr("href");
                            String recruitmentId = "topcv.rec" + job.attr("data-job-id");
                            log.info("Rec ID: {}", recruitmentId);
                            Recruitment recruitment = getRecruitmentDetail(recruitmentLink, recruitmentId);

                            // Create Recruitment Post
                            if (company != null && recruitment != null) {
                                if (!recruitmentPostRepository.existsByCompanyAndRecruitment(company, recruitment)) {
                                    RecruitmentPost post = RecruitmentPost.builder()
                                            .company(company)
                                            .recruitment(recruitment)
                                            .expireAt(recruitment.getApplicationDeadline())
                                            .build();
                                    recruitmentPostRepository.save(post);
                                } else {
                                    log.info("RecruitmentPost already exists. Skipping save.");
                                }
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
                currentPage++;
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
        if(type.contains("Thu nhập")) return "Salary";
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

    private final Map<String, String> simpleCustom = Map.of(
            "jobHeader",".box-header",
            "jobTags",".job-tags__group",
            "jobLocation", ".box-address",
            "jobDescription",".box-info",
            "jobRelatedTag1",".box-career",
            "jobRelatedTag2",".box-category-city",
            "jobGeneralInfo",".box-general-group"
    );

    private List<String> getDescriptions(Elements items, List<String> list){
        if(list == null) list = new ArrayList<>();

        // Change <br/> elements to "\\n"
        items.select("br").append("\\n");

        for(Element item : items){
            if(item.children().size() > 1 && !(item.children().select("br").size() > 1)) {
                // Recursive search if found child element(s)
                getDescriptions(item.children(), list);
            } else {
                // Skip empty element
                if(item.text().isEmpty()) continue;

                // Change "\\n" to "\n" for line break
                String line = item.text().replace("\\n", "\n");
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

            // ------------------------------- COMPANY SIGNATURES ------------------------------ //
            String companyLogo = doc.select(".company-logo").select("img").attr("src");
            String companyName = doc.select(".company-name").text();

            // ------------------------------ COMPANY INTRODUCTIONS ---------------------------- //
            Elements introContents;
            introContents = doc.select(".intro-content");
            if (introContents.isEmpty()) {
                introContents = doc.select(".company-info").select(".content");
            }
            List<String> companyIntro = getDescriptions(introContents, null);

            // ----------------------------- COMPANY GENERAL INFOS ----------------------------- //
            Map<String, String> companyInfo = new LinkedHashMap<>();
            Elements infos = doc.select(".box-info-company-general-item__info");
            for (Element info : infos) {
                String infoTitle = convertToEng(info.select(".box-info-company-general-item__info--title").text());
                if(infoTitle.equals("Mã số thuế")) {continue;}

                String infoValue = info.select(".box-info-company-general-item__info--desc").text();
                companyInfo.put(infoTitle, infoValue);
            }

            // -------------------------------- COMPANY CONTACTS -------------------------------- //
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
        boolean simple = false;

        if (doc != null) {
            Element jobInfo;

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
                
                if (jobInfo != null) {
                    premium = true;
                } else {
                    // Premium custom tags not found
                    // Proceed to get Simple custom tags
                    jobInfo = doc.selectFirst(".section-content-job-detail");
                    if (jobInfo != null) {
                        simple = true;
                    }
                }
            }

            if (jobInfo == null) {
                throw new NullPointerException("Parsing format not yet implemented for " + recruitmentLink);
            }

            Map<String, String> jobDetail;

            if (simple) jobDetail = simpleCustom;
            else if (premium) jobDetail = premiumCustom;
            else jobDetail = normalCustom;

            // -------------------------- Normal & Premium Custom Tags ------------------------- //
            String jobTitle = jobDetail.get("jobTitle");                // Title
            String jobBasicInfos = jobDetail.get("jobBasicInfos");      // Salary | Location | Experience
            String jobDeadline = jobDetail.get("applicationDeadline");  // Application deadline
            String jobRelatedTags = jobDetail.get("jobRelatedTags");    // Related tags

            // ------------------------------- Simple Custom Tags ------------------------------ //
            String jobHeader = jobDetail.get("jobHeader");              // Title | Salary | Application deadline
            String jobLocation = jobDetail.get("jobLocation");          // Location
            String jobRelatedTag1 = jobDetail.get("jobRelatedTag1");    // | Same part but different div class
            String jobRelatedTag2 = jobDetail.get("jobRelatedTag2");    // |

            // ----------------------------- Same for all 3 Customs ---------------------------- //
            String jobGeneralInfos = jobDetail.get("jobGeneralInfo");   // General infos
            String jobDescriptions = jobDetail.get("jobDescription");   // Descriptions
            String jobTags = jobDetail.get("jobTags");                  // Job tags


            // ----------------------------------- JOB HEADER ---------------------------------- //
            String recruitmentTitle;
            String recruitmentSalary;
            String recruitmentLocation = "";    // | Is empty when parsing Simple custom
            String recruitmentExperience = "";  // |
            LocalDate recruitmentDeadline;

            if(simple) {
                Elements header = jobInfo.select(jobHeader);
                recruitmentTitle = header.select(".title").text();
                recruitmentSalary = header.select(".salary").text();

                String dayTxt = header.select(".deadline").text().replaceAll("\\D+", "");
                int days = dayTxt.isEmpty() ? 30 : Integer.parseInt(dayTxt);

                recruitmentDeadline = LocalDate.now().plusDays(days);

            } else {
                recruitmentTitle = jobInfo.select(jobTitle).text();
                Elements basicInfos = jobInfo.select(jobBasicInfos);
                recruitmentSalary = !basicInfos.isEmpty() ? basicInfos.getFirst().text() : "N/A";
                recruitmentLocation = basicInfos.size() > 1 ? basicInfos.get(1).text() : "N/A";
                recruitmentExperience = basicInfos.size() > 2 ? basicInfos.getLast().text() : "N/A";

                // Get application deadline date
                String deadlineTxt = jobInfo.select(jobDeadline).text();
                if (!deadlineTxt.isEmpty()) {
                    recruitmentDeadline = LocalDate.parse(deadlineTxt, formatDate);
                } else {
                    recruitmentDeadline = LocalDate.now().plusDays(30);
                }
            }

            // ------------------------------------ JOB TAGS ----------------------------------- //
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

            // -------------------------------- JOB DESCRIPTIONS ------------------------------- //
            Map<String, List<String>> recruitmentDescription = new LinkedHashMap<>();
            Elements descriptions = jobInfo.select(jobDescriptions);

            if(simple) { // Simple custom
                // Recruitment Tags Section
                descriptions.removeFirst();

                // Work Location Section
                descriptions.addAll(jobInfo.select(jobLocation));

            } else {
                // Remove job application area
                descriptions.removeLast();
            }

            for(Element desc : descriptions) {
                String descName = convertToEng(desc.child(0).text());
                Element descText = desc.child(1);

                List<String> descLine = getDescriptions(descText.children(), null);
                recruitmentDescription.put(descName, descLine);
            }

            // ------------------------------- JOB GENERAL INFOS ------------------------------- //
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

            // -------------------------------- JOB RELATED TAGS ------------------------------- //
            Map<String, List<String>> recruitmentRelatedTag = new LinkedHashMap<>();
            Elements relatedTags;

            if(simple) { // Simple Custom edition
                relatedTags = jobInfo.select(jobRelatedTag1);
                relatedTags.addAll(jobInfo.select(jobRelatedTag2));
            } else {
                relatedTags = jobInfo.select(jobRelatedTags);
            }

            for(Element relatedTag : relatedTags) {
                String tagName = convertToEng(relatedTag.child(0).text());

                Elements tagList = relatedTag.child(1).children();
                List<String> tagItem = new ArrayList<>();
                for (Element tag : tagList) {
                    tagItem.add(tag.text());
                }
                recruitmentRelatedTag.put(tagName, tagItem);
            }

            // ------------------------ END RETRIEVING RECRUITMENT INFOS ----------------------- //
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
