package com.hacademy.twagent.chatbot.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hacademy.twagent.chatbot.service.ChatbotActionService;
import com.hacademy.twagent.chatbot.vo.ChatbotRequest;
import com.hacademy.twagent.chatbot.vo.ChatbotResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {
	
	@Autowired
	private ChatbotActionService chatbotActionService;
	
	@GetMapping("/running")
	public String running() {
		return "Chatbot running";
	}
	
	@PostMapping("/action")
	public ChatbotResponse action(@RequestBody ChatbotRequest request) {
		System.out.println("request = " + request);
		return chatbotActionService.parseAction(request);
		
//		test용 더미 요청
//		return ChatbotResponse.builder()
//					.type("action")
//					.action("navigate")
//					.params(
//						Map.of("path", "/")
//					)
//					.reply("홈 화면으로 이동합니다")
//				.build();
	}
}
