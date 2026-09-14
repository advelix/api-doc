package io.github.advelix.demo.controller;

import io.github.advelix.demo.model.CreateUserRequest;
import io.github.advelix.demo.model.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User endpoints.
 *
 * @author api-doc
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * Get a single user by id.
     *
     * @param id user id
     * @return the user
     */
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable("id") Long id) {
        UserResponse user = new UserResponse();
        user.setId(id);
        user.setName("demo-user");
        user.setEmail("demo@example.com");
        user.setStatus(UserResponse.Status.ACTIVE);
        return user;
    }

    /**
     * Create a new user.
     *
     * @param request user fields
     * @return the created user
     */
    @PostMapping
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        UserResponse user = new UserResponse();
        user.setId(1L);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setStatus(UserResponse.Status.ACTIVE);
        return user;
    }
}
