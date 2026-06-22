package org.example.dip2.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.dip2.dto.room.FinalGameReportResponse;
import org.example.dip2.dto.room.FinalPlayerReportResponse;
import org.example.dip2.dto.room.FinalQuestionDetailResponse;
import org.example.dip2.dto.room.FinalQuestionPlayerAnswerResponse;
import org.example.dip2.dto.room.FinalTeamReportResponse;
import org.example.dip2.dto.room.TeamQuestionScoreResponse;
import org.springframework.stereotype.Service;

@Service
public class FinalReportExportService {

    public byte[] export(FinalGameReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            createOverviewSheet(workbook, headerStyle, report);
            createQuestionsSheet(workbook, headerStyle, report);
            createTeamsSheet(workbook, headerStyle, report);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate final report workbook", exception);
        }
    }

    private void createOverviewSheet(Workbook workbook, CellStyle headerStyle, FinalGameReportResponse report) {
        Sheet sheet = workbook.createSheet("Overview");
        int rowIndex = 0;

        writeRow(sheet, rowIndex++, "Quiz", report.quizTitle());
        writeRow(sheet, rowIndex++, "Generated at", report.generatedAt() == null ? "" : report.generatedAt().toString());
        rowIndex++;
        writeHeader(sheet, headerStyle, rowIndex++, "Rank", "Player", "Team", "Correct answers", "Average response time (ms)", "Total response time (ms)");

        for (FinalPlayerReportResponse player : report.players()) {
            writeRow(
                    sheet,
                    rowIndex++,
                    player.rank(),
                    player.displayName(),
                    player.teamName(),
                    player.correctAnswers(),
                    player.averageResponseTimeMillis(),
                    player.totalResponseTimeMillis()
            );
        }

        autosize(sheet, 6);
    }

    private void createQuestionsSheet(Workbook workbook, CellStyle headerStyle, FinalGameReportResponse report) {
        Sheet sheet = workbook.createSheet("Questions");
        int rowIndex = 0;
        writeHeader(sheet, headerStyle, rowIndex++, "Question", "Player", "Answer", "Response time (ms)");

        for (int questionIndex = 0; questionIndex < report.questions().size(); questionIndex++) {
            FinalQuestionDetailResponse question = report.questions().get(questionIndex);
            for (FinalQuestionPlayerAnswerResponse answer : question.playerAnswers()) {
                writeRow(
                        sheet,
                        rowIndex++,
                        "Question " + (questionIndex + 1) + ": " + question.questionText(),
                        answer.displayName(),
                        answer.selectedAnswerText() == null ? "No answer" : answer.selectedAnswerText(),
                        answer.responseTimeMillis()
                );
            }
        }

        autosize(sheet, 4);
    }

    private void createTeamsSheet(Workbook workbook, CellStyle headerStyle, FinalGameReportResponse report) {
        Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName("Teams"));
        int rowIndex = 0;
        writeHeader(sheet, headerStyle, rowIndex++, "Team", "Total score", "Question ID", "Correct", "Confidence", "Multiplier", "Speed factor", "Points");

        for (FinalTeamReportResponse team : report.teams()) {
            if (team.questionScores().isEmpty()) {
                writeRow(sheet, rowIndex++, team.teamName(), team.totalScore(), "", "", "", "", "", "");
                continue;
            }
            for (TeamQuestionScoreResponse score : team.questionScores()) {
                writeRow(
                        sheet,
                        rowIndex++,
                        team.teamName(),
                        team.totalScore(),
                        score.questionId(),
                        score.correct(),
                        score.confidenceLevel(),
                        score.appliedMultiplier(),
                        score.speedFactor(),
                        score.pointsAwarded()
                );
            }
        }

        autosize(sheet, 8);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, int rowIndex, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
            Cell cell = row.createCell(columnIndex);
            cell.setCellValue(toText(values[columnIndex]));
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeRow(Sheet sheet, int rowIndex, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
            Object value = values[columnIndex];
            Cell cell = row.createCell(columnIndex);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                cell.setCellValue(bool);
            } else {
                cell.setCellValue(toText(value));
            }
        }
    }

    private String toText(Object value) {
        return value == null ? "" : value.toString();
    }

    private void autosize(Sheet sheet, int columns) {
        for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
            sheet.autoSizeColumn(columnIndex);
        }
    }
}
