package com.hacademy.twagent.chatbot.vo;

import java.util.Map;

import lombok.Builder;

@Builder
public record ChatbotResponse(
	String type,
	String action,
	Map<String, Object> params,
	String reply
) {

}
