package com.hacademy.twagent.chatbot.vo;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record ActionSpec(
	String name,
	String description,
	List<String> requiredParams,
	Map<String, Object> allowedValues,
	List<String> examples
) {

}
