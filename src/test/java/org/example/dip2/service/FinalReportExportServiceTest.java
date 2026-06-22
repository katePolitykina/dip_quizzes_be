package org.example.dip2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.dip2.dto.room.FinalGameReportResponse;
import org.example.dip2.dto.room.FinalPlayerReportResponse;
import org.example.dip2.dto.room.FinalQuestionDetailResponse;
import org.example.dip2.dto.room.FinalQuestionPlayerAnswerResponse;
import org.example.dip2.dto.room.FinalTeamReportResponse;
import org.example.dip2.dto.room.TeamQuestionScoreResponse;
import org.junit.jupiter.api.Test;

class FinalReportExportServiceTest {

    private final FinalReportExportService service = new FinalReportExportService();

    @Test
    void exportCreatesReadableWorkbook() throws Exception {
        FinalGameReportResponse report = new FinalGameReportResponse(
                "quiz-1",
                "Java Basics",
                Instant.parse("2026-06-22T10:15:30Z"),
                List.of(new FinalPlayerReportResponse("player-1", "Alice", "Team 1", 1, 1200, 1200.0, 1)),
                List.of(new FinalQuestionDetailResponse(
                        "question-1",
                        "What is JVM?",
                        List.of(new FinalQuestionPlayerAnswerResponse("player-1", "Alice", "answer-1", "Java Virtual Machine", 1200L))
                )),
                List.of(new FinalTeamReportResponse(
                        "team-1",
                        "Team 1",
                        42.0,
                        List.of(new TeamQuestionScoreResponse("question-1", "answer-1", "HIGH", true, 1.0, 2.0, 42.0))
                ))
        );

        byte[] workbookBytes = service.export(report);

        assertTrue(workbookBytes.length > 0);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertNotNull(workbook.getSheet("Overview"));
            assertNotNull(workbook.getSheet("Questions"));
            assertNotNull(workbook.getSheet("Teams"));
            assertEquals("Java Basics", workbook.getSheet("Overview").getRow(0).getCell(1).getStringCellValue());
            assertEquals("Alice", workbook.getSheet("Questions").getRow(1).getCell(1).getStringCellValue());
            assertEquals(42.0, workbook.getSheet("Teams").getRow(1).getCell(7).getNumericCellValue());
        }
    }
}
