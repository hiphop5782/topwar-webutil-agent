package com.hacademy.twagent.vo;

public record AiAllianceSummary(
    String allianceTag,
    String allianceName,
    Integer memberCount,
    Long totalPower,
    Double averageActivityScore,
    Integer coreCount,
    Integer activeCount,
    Integer watchCount,
    Integer lowCount
) {
}
