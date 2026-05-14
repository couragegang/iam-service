package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AcceptedEmpty(boolean accepted) {}
