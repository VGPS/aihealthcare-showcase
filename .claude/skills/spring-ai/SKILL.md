---
name: spring-ai
description: Use when working with Spring AI components, ChatClient, prompts, output parsers, or the AI port/adapter layer in the AIHealthcare project.
---

# Spring AI Skill

## Version
Spring AI 1.x (Milestone / GA) with Spring Boot 3.x

## Core Dependencies
```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-bom</artifactId>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

## ChatClient Pattern (preferred)
```java
@Component
public class NewsletterAiAdapter implements NewsletterAiPort {

    private final ChatClient chatClient;

    public NewsletterAiAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public NewsletterSection summarise(List<NewsArticle> articles) {
        String prompt = buildPrompt(articles);
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
        return parseSection(response);
    }
}
```

## Structured Output (BeanOutputConverter)
```java
var converter = new BeanOutputConverter<>(NewsletterSection.class);
String response = chatClient.prompt()
    .user(u -> u.text(prompt + "\n" + converter.getFormat()))
    .call()
    .content();
NewsletterSection section = converter.convert(response);
```

## Testing — Mock the Port, NOT ChatClient
```java
// In unit tests, mock NewsletterAiPort (the domain port)
@Mock NewsletterAiPort aiPort;
given(aiPort.summarise(anyList())).willReturn(new NewsletterSection(...));

// Do NOT mock ChatClient in unit tests — that belongs only in integration/smoke tests
```

## AI Smoke Test Profile
```xml
<!-- pom.xml in infrastructure/ai module -->
<profiles>
  <profile>
    <id>ai-smoke</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <configuration>
            <groups>ai-smoke</groups>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

## Configuration (application.yml)
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.3
```

## Key Interfaces
- `ChatClient` — primary high-level API
- `ChatModel` — lower-level model abstraction
- `PromptTemplate` — for parameterised prompts
- `BeanOutputConverter<T>` — structured JSON output → Java type
