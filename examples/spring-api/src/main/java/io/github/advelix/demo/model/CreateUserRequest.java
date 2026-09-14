package io.github.advelix.demo.model;

/**
 * Request payload for creating a user.
 */
public class CreateUserRequest {

    /**
     * Display name.
     */
    private String name;

    /**
     * E-mail address.
     */
    private String email;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
