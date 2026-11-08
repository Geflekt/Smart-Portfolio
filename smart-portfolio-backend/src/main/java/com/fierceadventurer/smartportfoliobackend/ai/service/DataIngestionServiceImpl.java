package com.fierceadventurer.smartportfoliobackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fierceadventurer.smartportfoliobackend.content.entity.BioCache;
import com.fierceadventurer.smartportfoliobackend.content.entity.Experience;
import com.fierceadventurer.smartportfoliobackend.content.repository.BioCacheRepository;
import com.fierceadventurer.smartportfoliobackend.content.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataIngestionServiceImpl implements DataIngestionService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final BioCacheRepository bioCacheRepository;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    @Transactional
    public void ingestPdf(MultipartFile file) {
        log.info("Starting pdf ingestion and LLM parsing process for file: {}", file.getOriginalFilename());
        try {
            // 1. Extract text from PDF
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(file.getResource());
            List<Document> extractedPages = pdfReader.get();
            log.info("Successfully extracted {} pages from the PDF.", extractedPages.size());

            // 2. Vector Store Chunking and Embedding (for RAG Chatbot)
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunkedDocuments = splitter.apply(extractedPages);
            log.info("Split PDF into {} chunks. Saving embeddings to pgvector...", chunkedDocuments.size());
            vectorStore.add(chunkedDocuments);

            // 3. LLM Parsing: Automatically extract Bio, Role, Education, and Work Experiences
            String fullResumeText = extractedPages.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            parseAndSaveResumeWithLlm(fullResumeText);

            log.info("Successfully completed PDF ingestion, pgvector storage, and LLM portfolio extraction!");

        } catch (Exception e) {
            log.error("Failed to parse and ingest PDF document", e);
            throw new RuntimeException("Failed to process PDF file. Ensure it is a valid text-based PDF.", e);
        }
    }

    private void parseAndSaveResumeWithLlm(String resumeText) {
        ChatClient chatClient = chatClientBuilder.build();
        String candidateName = "Ayush Tripathi";

        try {
            log.info("Sending resume text to LLM (Groq) for automated bio & experience extraction...");

            String prompt = """
                    You are an expert resume parser for a software engineer portfolio.
                    Analyze the following resume text and extract the candidate's profile and work experiences.
                    Return STRICTLY a JSON object matching this exact format (NO markdown formatting, NO backticks, NO commentary):
                    {
                      "name": "Full Name",
                      "role": "Primary role e.g. Backend Developer",
                      "education": "Degree, major and college/university",
                      "bio": "A concise, high-impact 2-3 sentence terminal bio summarizing skills, backend expertise, core technologies, and goals.",
                      "experiences": [
                        {
                          "role": "Job / Internship Title",
                          "company": "Company / Institute Name",
                          "period": "Duration e.g. Jan 2026 - Present",
                          "description": "Concise summary of project / work done and key impact",
                          "techStack": "Comma-separated technologies e.g. Java, Spring Boot, React, Docker"
                        }
                      ]
                    }

                    RESUME TEXT:
                    """ + resumeText;

            String rawResponse = chatClient.prompt().user(prompt).call().content();

            String name = "Ayush Tripathi";
            String role = "Backend Developer";
            String education = "";
            String bio = "";

            if (rawResponse != null && !rawResponse.isBlank()) {
                String cleanedJson = rawResponse.trim();
                int firstBrace = cleanedJson.indexOf('{');
                int lastBrace = cleanedJson.lastIndexOf('}');
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    cleanedJson = cleanedJson.substring(firstBrace, lastBrace + 1);
                }

                try {
                    JsonNode root = objectMapper.readTree(cleanedJson);
                    name = root.path("name").asText(name);
                    role = root.path("role").asText(role);
                    education = root.path("education").asText(education);
                    bio = root.path("bio").asText(bio);
                    candidateName = name;

                    // Update Work Experiences in PostgreSQL
                    JsonNode experiencesNode = root.path("experiences");
                    if (experiencesNode.isArray() && experiencesNode.size() > 0) {
                        experienceRepository.deleteAll();
                        int order = 1;
                        for (JsonNode expNode : experiencesNode) {
                            Experience exp = Experience.builder()
                                    .role(expNode.path("role").asText())
                                    .company(expNode.path("company").asText())
                                    .period(expNode.path("period").asText())
                                    .description(expNode.path("description").asText())
                                    .techStack(expNode.path("techStack").asText())
                                    .sortOrder(order++)
                                    .build();
                            experienceRepository.save(exp);
                        }
                        log.info("Saved {} work experiences extracted by LLM from resume.", experiencesNode.size());
                    }
                } catch (Exception parseEx) {
                    log.warn("Failed to parse JSON tree from LLM response, will generate direct bio: {}", parseEx.getMessage());
                }
            }

            // If bio was blank or missing, generate dedicated terminal bio
            if (bio == null || bio.isBlank()) {
                bio = generateTerminalBioWithLlm(resumeText, candidateName, chatClient);
            }

            // Save extracted bio to LRU bio cache
            if (bio != null && !bio.isBlank()) {
                bioCacheRepository.save(BioCache.builder()
                        .bio(bio)
                        .lastAccessedAt(java.time.LocalDateTime.now())
                        .build());
                log.info("Saved extracted resume bio to bio_cache: {}", bio);
            }

            // Evict caches so frontend immediately sees the newly parsed data
            evictCaches();

        } catch (Exception e) {
            log.error("Failed to parse experiences from resume with LLM, attempting direct bio synthesis fallback...", e);
            try {
                String fallbackBio = generateTerminalBioWithLlm(resumeText, candidateName, chatClient);
                if (fallbackBio != null && !fallbackBio.isBlank()) {
                    bioCacheRepository.save(BioCache.builder()
                            .bio(fallbackBio)
                            .lastAccessedAt(java.time.LocalDateTime.now())
                            .build());
                    evictCaches();
                    log.info("Fallback bio saved to bio_cache: {}", fallbackBio);
                }
            } catch (Exception ex) {
                log.error("Direct bio generation fallback failed: {}", ex.getMessage());
            }
        }
    }

    private String generateTerminalBioWithLlm(String resumeText, String candidateName, ChatClient chatClient) {
        String excerpt = resumeText.length() > 3500 ? resumeText.substring(0, 3500) : resumeText;
        String bioPrompt = """
                You are a senior technical writer crafting a high-impact terminal bio for %s based on their resume.

                RESUME:
                %s

                CRITICAL INSTRUCTIONS:
                - Write a concise, compelling 2-3 sentence terminal bio summarizing engineering mindset, core backend stack (Java, Spring Boot, Microservices, Kafka, Docker, PostgreSQL), and distributed systems skills.
                - Format specifically for an interactive developer terminal.
                - Return ONLY the raw bio text. Do NOT include greetings, quotation marks, markdown code fences, or any other commentary.
                """.formatted(candidateName, excerpt);

        try {
            String bio = chatClient.prompt().user(bioPrompt).call().content();
            if (bio != null && !bio.isBlank()) {
                return bio.trim().replaceAll("^\"|\"$", "").replace("```", "").trim();
            }
        } catch (Exception e) {
            log.error("Direct terminal bio generation failed: {}", e.getMessage());
        }
        return null;
    }

    private void evictCaches() {
        if (cacheManager.getCache("portfolio_profile") != null) {
            cacheManager.getCache("portfolio_profile").clear();
        }
        if (cacheManager.getCache("portfolio_experiences") != null) {
            cacheManager.getCache("portfolio_experiences").clear();
        }
    }
}
