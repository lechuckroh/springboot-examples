# Spring Boot Redis TTL

Redis 를 데이터 소스와 캐시 저장소로 사용하는 경우, TTL 을 적용하는 방법에 대해서 알아봅니다.

## Context

* `User` 클래스는 사용자 정보는 나타내며, H2 데이터베이스에 저장합니다.
* `/users*` 엔드포인트는 Redis 캐시를 사용해 응답을 캐싱하며, TTL 을 적용합니다.
* `Session` 클래스는 사용자 세션 정보를 나타내며, TTL 을 적용해 Redis 에 저장합니다.
* Redis 를 다른 서비스와 같이 사용할 수 있도록, 저장시 Key 에 지정한 Prefix 를 사용합니다.

## Spring Data 설정

Spring Data 를 사용해 Redis 에 데이터를 저장할 때 TTL과 Key Prefix 를 적용하는 방법에 대해서 알아봅니다.

### 1. Redis Configuration

먼저 `RedisTemplate` configuration 을 추가합니다.

```kotlin
@Configuration
class RedisConfiguration {
    @Bean
    fun getRedisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, Session> {
        val redisTemplate: RedisTemplate<String, Session> = RedisTemplate()
        redisTemplate.connectionFactory = factory
        return redisTemplate
    }
}
```

### 2. Redis 모델 정의

Redis 에 저장할 `Session` 모델을 정의합니다.

`@RedisHash` 애노테이션의 `timeToLive` 속성에 TTL 값을 초(second) 단위로 지정합니다.
이렇게 하면 모든 `Session` 객체를 Redis에 저장시 `60`초의 TTL이 설정됩니다.

```kotlin
@RedisHash(timeToLive = 60)
class Session(
        @Id var id: String,
        var username: String,
)
```

### 3. Redis Repository 정의

```kotlin
interface SessionRepository : ListCrudRepository<Session, String>
```

### 4. KeyspaceSettings 설정

`KeyspaceSettings`을 사용해 `Session` 클래스의 TTL을 설정할 수 있습니다.
Keyspace 는 Redis Hash 키 생성시 사용할 prefix 를 정의합니다.

```kotlin
@Configuration
@EnableRedisRepositories(keyspaceConfiguration = MyKeyspaceConfiguration::class)  // 3. KeyspaceConfiguration 지정
class RedisConfiguration {
    // ...
}

const val REDIS_PREFIX = "demo"

class MyKeyspaceConfiguration : KeyspaceConfiguration() {
    override fun initialConfiguration(): Iterable<KeyspaceSettings> {
        val settings = KeyspaceSettings(Session::class.java, "$REDIS_PREFIX:session")  // 1. prefix 설정
        settings.setTimeToLive(120)  // 2. TTL 설정
        return listOf(settings)
    }
}
```

1. `KeyspaceSettings` 생성시 `Session` 클래스의 keyspace 를 전달하게 되는데, keyspace 에 서비스에서 사용할 prefix 를 추가합니다.
2. `setTimeToLive` 메서드를 사용해 `Session` 클래스 전체에 대한 TTL 을 설정합니다.
   * `@RedisHash`, `KeyspaceSettings` 모두 TTL 이 설정된 경우 `@RedisHash` 애노테이션 설정값이 우선합니다.
   * 두가지 방법 모두 `Session` 클래스 전체에 대해서 TTL 을 설정하는 것은 동일합니다.
   * `KeyspaceSettings` 를 사용하는 경우, 여러 클래스의 TTL 을 한 곳에서 설정할 수 있다는 장점이 있습니다.
3. `@EnableRedisRepositories` 애노테이션을 사용해 커스텀 `KeyspaceConfiguration` 을 지정합니다.

### 5. Controller

Redis 캐시 저장 확인을 위해 HTTP 엔드포인트를 추가합니다.

`POST` 요청으로 Redis 에 세션 정보를 저장하고, `GET` 요청을 사용해 세션 정보를 조회합니다. 

```kotlin
@RestController
@RequestMapping("/sessions")
class SessionController(val repo: SessionRepository) {
    @GetMapping("{id}")
    fun getSession(@PathVariable("id") id: String): Optional<Session> {
        return this.repo.findById(id)
    }

    @PostMapping("{id}")
    fun setSession(@PathVariable("id") id: String) {
        this.repo.save(Session(id = id, username = "user-${id}"))
    }
}
```

### 6. 실행

먼저 개발 서버를 실행합니다:

```shell
$ ./gradlew bootRun
```

다른 터미널을 열고 세션 저장을 위해 `POST` 요청을 보냅니다.
여기서는 [httpie](https://httpie.io/)를 사용합니다:

```shell
$ http POST :8080/sessions/1
```

마지막으로 `redis-cli` 를 실행해 redis 에 저장된 값을 확인합니다:

```shell
# redis-cli 실행
$ docker compose exec redis redis-cli

# 전체 key 목록 확인
127.0.0.1:6379> keys *
1) "demo:session:1"
2) "demo:session:1:phantom"
3) "demo:session"

# TTL 확인
127.0.0.1:6379> ttl demo:session:1
(integer) 49
```

`ttl` 명령을 사용해 `@RedisHash` 애노테이션에 설정된 값이 적용된 것을 확인할 수 있습니다.

## Caching

캐싱을 사용하는 경우 TTL 설정 방법에 대해서 알아봅니다.

### 1. 캐싱 활성화

캐시를 활성화하려면 `CachingConfigurer` 를 구현한 Configuration 클래스에 `@EnableCaching` 애노테이션을 추가합니다.

이렇게 하면 애플리케이션 시작시, 모든 스프링 Bean을 검색하면서 public 메서드에 캐싱 애노테이션이 있는지 검사하는 후처리 작업을 실행합니다.

```kotlin
@EnableCaching
@Configuration
class ApplicationConfig : CachingConfigurer
```

### 2. `application.yaml` 설정

마이크로서비스 환경에서는 하나의 Redis 인스턴스를 여러 서비스가 공유해 사용하는 경우가 많습니다.
이 경우, 서비스간의 Key가 충돌하는 것을 방지하기 위해 서비스 별로 key prefix 를 정해서 사용하는 것을 추천합니다.

`application.yaml` 파일을 다음과 같이 설정합니다.

```yaml
spring:
  cache:
    redis:
      key-prefix: "demo:"
      time-to-live: 120000
```

* `spring.cache.redis.key-prefix`: 응답을 캐싱할 때 사용할 prefix 키를 지정합니다.
* `spring.cache.redis.time-to-live`: 응답을 캐싱할 때 사용할 TTL 값을 밀리초(millisecond) 단위로 지정합니다.

### 3. `@Cacheable`

```kotlin
@RestController
@RequestMapping("/sessions")
@CacheConfig(cacheNames = ["sessions"])
class SessionController(val repo: SessionRepository) {
    @GetMapping("{id}")
    @Cacheable
    fun getSession(@PathVariable("id") id: String): Optional<Session> {
        return this.repo.findById(id)
    }
}
```

위와 같이 `getSession` 메서드에 `@Cacheable` 애노테이션을 추가한 경우, Redis 에 생성되는 캐싱 key 값은 `{key-prefix}{cacheNames}::{id}` 형식이 됩니다.

예를 들어, `GET /sessions/3` 을 호출한 경우, `demo:sessions::3` 이라는 Redis key 에 캐싱 데이터가 저장됩니다.

### 4. `KeyGenerator` 정의

캐싱 키는
기본적으로 [SimpleKeyGenerator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/cache/interceptor/SimpleKeyGenerator.html)
를 사용해 캐시 키를 생성합니다.

마이크로서비스 환경에서는 여러 프레임워크들을 사용해 서비스가 구현될 수 있기 때문에, 캐싱 키를 생성하는 규칙도 서비스 별로 제각각 다를 수 있습니다.
서비스별로 캐싱 키 생성 규칙을 동일하게 가져가려면, 기본 제공되는 `SimpleKeyGenerator` 대신 커스텀 KeyGenerator 를 정의해야 합니다.

먼저 `KeyGenerator` 인터페이스를 구현한 `CacheKeyGenerator` 라는 이름의 커스텀 KeyGenerator 클래스를 정의합니다.

```kotlin
class CacheKeyGenerator : KeyGenerator {
  override fun generate(target: Any, method: Method, vararg params: Any?): Any {
    return "${target.javaClass.simpleName}_${method.name}_${StringUtils.arrayToDelimitedString(params, "_")}"
  }
}
```

정의한 `CacheKeyGenerator`를 등록하기 위해서는 `KeyGenerator` 타입 Bean 을 생성해야 합니다.
아래 코드에서는 `cacheKeyGenerator` 라는 이름으로 Bean 을 등록합니다:

```kotlin
@EnableCaching
@Configuration
class ApplicationConfig : CachingConfigurer {
  @Bean(name = ["cacheKeyGenerator"])
  override fun keyGenerator(): KeyGenerator {
    return CacheKeyGenerator()
  }
}
```

이렇게 등록한 커스텀 KeyGenerator는 모든 캐싱 항목에 적용됩니다.

```kotlin
@CacheConfig(cacheNames = ["sessions"])
class SessionController(val repo: SessionRepository) {
    @GetMapping("{id}") @Cacheable
    fun getSession(@PathVariable("id") id: String): Optional<Session> { /** ... */ }
}
```

`getSession()` 함수 호출 결과를 캐싱하기 위해 생성된 키의 패턴은 `{key-prefix}{cacheName}::{generatedKey}` 가 됩니다.

예를 들어, `GET /sessions/3` 을 호출한 경우, `demo:sessions::SessionController_getSession_3` 이라는 Redis key 에 캐싱 데이터가 저장됩니다.
