package app.cairn.api.orgs.invite.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin request to invite an email address. */
public record CreateInviteRequest(@NotBlank @Email @Size(max = 320) String email) {}
