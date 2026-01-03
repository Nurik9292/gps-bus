package biz.ugur.busroutebackend.transport.application.dto.assignment;

public record ImportFromExcelCommand(
        byte[] fileContent,
        String assignedBy
) {
}
