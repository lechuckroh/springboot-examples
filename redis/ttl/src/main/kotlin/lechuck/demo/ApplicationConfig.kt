package lechuck.demo

import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.context.annotation.Bean

@EnableCaching
@Configuration
class ApplicationConfig : CachingConfigurer {
    @Bean(name = ["cacheKeyGenerator"])
    override fun keyGenerator(): KeyGenerator {
      return CacheKeyGenerator()
    }
}