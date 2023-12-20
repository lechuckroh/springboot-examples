package lechuck.demo

import java.time.Duration
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.cache.RedisCacheConfiguration

@EnableCaching
@Configuration
class ApplicationConfig : CachingConfigurer {
    @Bean(name = ["cacheKeyGenerator"])
    override fun keyGenerator(): KeyGenerator {
      return CacheKeyGenerator()
    }

  /** RedisCache 디폴트 설정 커스터마이즈 */
  @Bean
  fun redisCacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer {
    return RedisCacheManagerBuilderCustomizer { builder ->
      builder.withCacheConfiguration(
          "sessions",
          RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(3)))
    }
  }
}