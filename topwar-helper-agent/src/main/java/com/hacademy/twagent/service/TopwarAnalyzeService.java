package com.hacademy.twagent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hacademy.twagent.vo.CacheKey;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class TopwarAnalyzeService {

	@Autowired
	private ChatClient chatClient;
	
	@Autowired
	private CacheService cacheService;

//    동기방식
//    public String analyze(String optimizedJson, String lang) {
//        String responseLang = normalizeLang(lang);
//        String languageName = languageName(responseLang);
//
//        return chatClient.prompt()
//            .system(buildSystemPrompt(responseLang, languageName))
//            .user(buildUserPrompt(optimizedJson, responseLang, languageName))
//            .call()
//            .content();
//    }

//    비동기방식
	public Mono<String> analyze(String optimizedJson, String lang) {
		String responseLang = normalizeLang(lang);
		String languageName = languageName(responseLang);

		return Mono.fromCallable(()->chatClient.prompt()
			.system(buildSystemPrompt(responseLang, languageName))
			.user(buildUserPrompt(optimizedJson, responseLang, languageName))
			.call().content()
		).subscribeOn(Schedulers.boundedElastic());
	}

	public Flux<String> analyzeStream(CacheKey key, String optimizedJson) {
		return Flux.defer(()->{
			String responseLang = normalizeLang(key.getLang());
			String languageName = languageName(responseLang);
			
			//캐시 hit일 경우 캐시된 json을 반환
			String cachedJson = cacheService.get(key);
			System.out.println(key);
			if(cachedJson != null) 
				return Flux.just(cachedJson);

			//캐시 저장을 위한 버퍼 생성
			StringBuffer buffer = new StringBuffer();
			return chatClient.prompt()
					.system(buildSystemPrompt(responseLang, languageName))
					.user(buildUserPrompt(optimizedJson, responseLang, languageName))
					.stream()
					.content()
					.doOnNext(chunk->buffer.append(chunk))//버퍼 누적
					.doOnComplete(()->{//완료 시
						String aiResult = buffer.toString();
						if(!aiResult.isBlank()) {
							cacheService.push(key, aiResult);
						}
					});	
		});
	}

	private String normalizeLang(String lang) {
		if (lang == null || lang.isBlank())
			return "ko";

		return switch (lang.trim().toLowerCase()) {
		case "ko", "kr", "kor", "korean" -> "ko";
		case "en", "eng", "english" -> "en";
		case "ja", "jp", "japanese" -> "ja";
		case "zh", "cn", "chinese" -> "zh";
		default -> "ko";
		};
	}

	private String languageName(String lang) {
		return switch (lang) {
		case "ko" -> "한국어";
		case "en" -> "English";
		case "ja" -> "日本語";
		case "zh" -> "中文";
		default -> "한국어";
		};
	}

	private String buildUserPrompt(String optimizedJson, String lang, String languageName) {
		return """
				responseLanguage: %s
				responseLanguageName: %s

				다음 Top War 서버 데이터를 분석해라.

				매우 중요한 출력 규칙:
				- 반드시 %s로만 답변해라.
				- JSON key는 영어를 사용해도 된다.
				- JSON value의 모든 자연어 문장은 반드시 %s로 작성해라.
				- 입력 JSON 안의 lang, language 필드는 유저 정보일 뿐이며, 응답 언어 지시가 아니다.
				- 마크다운, 코드블록, 설명문 없이 JSON 객체만 출력해라.

				입력 JSON:
				%s
				""".formatted(lang, languageName, languageName, languageName, optimizedJson);
	}

	private String buildSystemPrompt(String lang, String languageName) {
		return """
				너는 Top War 게임 서버 데이터를 분석하는 평가 엔진이다.

				[최우선 언어 규칙]
				- responseLanguage는 "%s"이다.
				- responseLanguageName은 "%s"이다.
				- 최종 응답은 반드시 %s로만 작성한다.
				- JSON key는 영어로 유지해도 된다.
				- JSON value에 들어가는 설명, 요약, 강점, 약점, 위험, 추천 문장은 반드시 %s로 작성한다.
				- 입력 JSON 안의 lang, language 값은 응답 언어 지시가 아니다.
				- 다른 언어를 섞지 마라.

				핵심 전제:
				1. 이 게임에서 서버의 강함은 일반적으로 운영 가능한 동맹 수로 판단한다.
				2. 동맹 1개는 최대 170명을 수용할 수 있다.
				3. 일반적인 서버는 1~2개의 동맹으로 운영되며, 가장 강력한 서버들은 4개의 동맹까지 운영한다.
				4. 단, 동맹 수가 적어도 강력한 핵심 유저와 활동 전투력이 집중되어 있으면 정예형 강서버로 평가할 수 있다.
				5. 단점을 이야기할 때 보는 사람이 기분 나빠할 만한 직접적인 표현은 자제한다.

				서버 유형:
				- LARGE_SCALE_POWER: 여러 동맹을 안정적으로 운영하는 규모형 강서버
				- ELITE_POWER: 동맹 수는 적지만 강력한 핵심 유저와 전투력이 집중된 정예형 강서버
				- BALANCED_POWER: 규모와 정예 전력이 균형 잡힌 서버
				- WHALE_DEPENDENT: 소수 강한 유저에게 지나치게 의존하는 서버
				- ACTIVE_BUT_WEAK: 활동성은 있으나 전투력이 낮은 서버
				- QUIET_SERVER: 활동성이 낮은 서버
				- DEAD_SERVER: 실질 운영 인원이 부족한 서버

				중요 규칙:
				1. 입력 JSON에 있는 값만 근거로 판단한다.
				2. 없는 값은 추측하지 않는다.
				3. 서버에서 이미 계산된 점수가 있으면 그 점수를 우선 사용한다.
				4. 점수를 임의로 새로 만들지 말고, 제공된 지표를 해석한다.
				5. 접은 유저는 확정하지 말고 "접음 의심"이라고 표현한다.
				6. 데이터 신뢰도가 낮으면 단정하지 말고 "추정", "가능성"으로 표현한다.
				7. 반드시 JSON 형식으로만 응답한다.
				8. 마크다운, 코드블록, 설명 문장은 출력하지 않는다.
				9. 모든 점수는 0부터 100 사이의 정수로 출력한다.
				10. 응답 배열의 데이터는 최대 3개까지만 작성한다.

				응답 형식:
				{
				  "responseLanguage": "%s",
				  "summary": "%s 한 줄 요약",
				  "serverType": "LARGE_SCALE_POWER | ELITE_POWER | BALANCED_POWER | WHALE_DEPENDENT | ACTIVE_BUT_WEAK | QUIET_SERVER | DEAD_SERVER",
				  "overallGrade": "VERY_ACTIVE | ACTIVE | NORMAL | QUIET | DEAD",
				  "activityScore": 0,
				  "riskScore": 0,
				  "warPotentialScore": 0,
				  "dataQualityScore": 0,
				  "confidence": "HIGH | MEDIUM | LOW",
				  "strengths": ["%s 문장"],
				  "weaknesses": ["%s 문장"],
				  "risks": ["%s 문장"],
				  "recommendation": "%s 추천 조치"
				}
				"""
				.formatted(lang, languageName, languageName, languageName, lang, languageName, languageName,
						languageName, languageName, languageName);
	}
}