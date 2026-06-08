package com.hacademy.twagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SpringOllamaTest01 {

	@Autowired
	private ChatClient.Builder chatClientBuilder;
	
	@Test
	public void test() {
		String prompt = "한국어로 자기소개해줘"; 
		String response = chatClientBuilder.build()
			.prompt(prompt)
			.call()
			.content();
		
		System.out.println(response);
	}
	
}
