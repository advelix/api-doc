package io.github.advelix.demo.model;

/**
 * User representation.
 */
public class UserResponse {

    /**
     * User identifier.
     */
    private Long id;

    /**
     * Display name.
     */
    private String name;

    /**
     * E-mail address.
     */
    private String email;

    /**
     * Account status.
     */
    private Status status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Account status.
     */
    public enum Status {
        /**
         * Account is active.
         */
        ACTIVE,
        /**
         * Account is disabled.
         */
        DISABLED
    }
}
