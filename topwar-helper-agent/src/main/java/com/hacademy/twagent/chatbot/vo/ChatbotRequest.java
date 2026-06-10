package com.hacademy.twagent.chatbot.vo;

import java.util.List;

import lombok.Builder;

@Builder
public record ChatbotRequest(
	String message,
	String lang,
	List<ActionSpec> actions
) {

}
