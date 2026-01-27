import { useState } from "react";
import { jwtDecode } from "jwt-decode"

export default function useToken() {
    const getToken = () => {
        const tokenString = localStorage.getItem('token');
        if (!tokenString) {
            return null;
        }

        const userToken = JSON.parse(tokenString);
        const token = userToken?.token;

        if (!token) {
            return null;
        }

        // Decode the token to check expiration
        try {
            const decoded = jwtDecode(token);
            const currentTime = Date.now() / 1000; // Convert to seconds

            // If token is expired, remove it and return null
            if (decoded.exp < currentTime) {
                localStorage.removeItem('token');
                return null;
            }

            return token;
        } catch (error) {
            // If decoding fails, token is invalid
            localStorage.removeItem('token');
            return null;
        }
    };

    const [token, setToken] = useState(getToken());

    const saveToken = userToken => {
        localStorage.setItem('token', JSON.stringify(userToken));
        setToken(userToken.token);
    };

    const removeToken = () => {
        localStorage.removeItem('token');
        setToken(null);
    };

    return {
        setToken: saveToken,
        token,
        removeToken
    };
}

