package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.LoginRequestDTO;
import com.project.smartsearchsystem.dto.LoginResponseDTO;
import com.project.smartsearchsystem.dto.RegisterRequestDTO;
import com.project.smartsearchsystem.dto.UserResponseDTO;

public interface UserService {
    LoginResponseDTO login(LoginRequestDTO loginRequest);

    UserResponseDTO register(RegisterRequestDTO registerRequest);

    UserResponseDTO getCurrentUser(String username);
}
