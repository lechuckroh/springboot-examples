package lechuck.demo.session

import org.springframework.data.repository.ListCrudRepository

interface SessionRepository : ListCrudRepository<Session, String>