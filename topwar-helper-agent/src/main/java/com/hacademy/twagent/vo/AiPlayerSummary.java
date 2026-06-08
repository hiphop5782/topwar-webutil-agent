package com.hacademy.twagent.vo;

public record AiPlayerSummary(
    String nickname,
    String allianceTag,
    Long power,
    Integer level,
    Integer activityScore,
    String activityGrade,
    Integer isOnline
) {
}