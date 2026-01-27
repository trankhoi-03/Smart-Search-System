import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";


function Login({ setToken }) {
    const [username, setUsername] = useState("")
    const [password, setPassword] = useState("")
    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)

    const navigate = useNavigate()

    const login = async (credentials) => {
        const response = await fetch('http://localhost:8080/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(credentials)
        })

        if (!response.ok) {
            throw new Error(`Login failed: ${response.statusText}`)
        }
        return await response.json()
    }

    const handleLogin = async (e) => {
        e.preventDefault()
        setError(null)
        setLoading(true)

        try {
            const token = await login({
                username, password
            })
            setToken(token)
            navigate('/home')
        } catch (err) {
            setError(err.message || 'An error occurred during login')
        } finally {
            setLoading(false)
        }
    }

    return(
        <div className="login-container">
            <div className="anchorPane">
                <h2 className="login-header">Login</h2>
                {error && <div className="error-message">{error}</div>}
                <form className="login-form" onSubmit={handleLogin}>
                    <input className="username" type="text" placeholder="Username"
                           onChange={(e) => setUsername(e.target.value)}
                           disabled={loading}/>

                    <input className="password" type="password" placeholder="Password"
                           onChange={(e) => setPassword(e.target.value)}
                           disabled={loading}/>

                    <button className="login-button" type="submit" disabled={loading}>{loading ? 'Logging in...' : 'Log In'}</button>
                    <div style={{marginTop: "10px"}}>
                        <Link to="/register">Don't have an account yet? Register one</Link>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default Login