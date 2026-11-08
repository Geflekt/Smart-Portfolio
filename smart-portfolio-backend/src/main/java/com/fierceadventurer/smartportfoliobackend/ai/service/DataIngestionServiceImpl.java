package com.fierceadventurer.smartportfoliobackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fierceadventurer.smartportfoliobackend.content.dto.ProfileRequest;
import com.fierceadventurer.smartportfoliobackend.content.entity.Experience;
import com.fierceadventurer.smartportfoliobackend.content.repository.ExperienceRepository;
import com.fierceadventurer.smartportfoliobackend.content.service.ProfileService;
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
    private final ProfileService profileService;
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
        try {
            log.info("Sending resume text to LLM (Groq Llama-3) for automated bio & experience extraction...");

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

            ChatClient chatClient = chatClientBuilder.build();
            String rawResponse = chatClient.prompt().user(prompt).call().content();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("LLM returned empty response for resume parsing.");
                return;
            }

            // Strip code fences if LLM wrapped output in ```json ... ```
            String cleanedJson = rawResponse.trim();
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7);
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3);
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            }
            cleanedJson = cleanedJson.trim();

            JsonNode root = objectMapper.readTree(cleanedJson);

            // Update Profile Bio in PostgreSQL
            String name = root.path("name").asText("Ayush Tripathi");
            String role = root.path("role").asText("Backend Developer");
            String education = root.path("education").asText("");
            String bio = root.path("bio").asText("");

            ProfileRequest profileReq = new ProfileRequest(name, role, education, bio, null, null, null, null, null, null, null);
            profileService.updateProfile(profileReq);
            log.info("Updated profile from LLM extraction: Name='{}', Role='{}'", name, role);

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

            // Evict caches so frontend immediately sees the newly parsed data
            if (cacheManager.getCache("portfolio_profile") != null) {
                cacheManager.getCache("portfolio_profile").clear();
            }
            if (cacheManager.getCache("portfolio_experiences") != null) {
                cacheManager.getCache("portfolio_experiences").clear();
            }

        } catch (Exception e) {
            log.error("Failed to parse resume with LLM, pgvector chunking still succeeded.", e);
        }
    }
}
