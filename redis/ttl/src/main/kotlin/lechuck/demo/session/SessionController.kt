package lechuck.demo.session

import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/sessions")
@CacheConfig(cacheNames = ["sessions"])
class SessionController(val repo: SessionRepository) {
    @GetMapping("{id}")
    @Cacheable
    fun getSession(@PathVariable("id") id: String): Optional<Session> {
        return this.repo.findById(id)
    }

    @PostMapping("{id}")
    fun setSession(@PathVariable("id") id: String) {
        this.repo.save(Session(id = id, username = "user-${id}"))
    }
}
