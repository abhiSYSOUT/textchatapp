package com.sample.repository;

import com.sample.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Find all users, ordered by status (so we can maybe put ONLINE first) and then
    // username
    List<User> findAllByOrderByStatusAscUsernameAsc();
}
