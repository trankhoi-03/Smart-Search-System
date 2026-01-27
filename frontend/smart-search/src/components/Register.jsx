import {useState} from "react";
import {Link} from "react-router-dom";

function Register() {
    const [firstName, setFirstName] = useState("")
    const [lastName, setLastName] = useState("")
    const [email, setEmail] = useState("")
    const [username, setUsername] = useState("")
    const [password, setPassword] = useState("")

    const register = async (credentials) => {
        return fetch('http://localhost:8080/auth/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(credentials)
        })
            .then(data => data.json())
    }

    const handleRegister = async (e) => {
        e.preventDefault();

        try {
            await register({ username, email, password, firstName, lastName })
            alert("Register Successful")
        } catch(error) {
            alert("Registration Failed")
        }
    }

    return(
        <div className="register-container">
            <div className="anchorPane">
                <h2 className="register-header">Register</h2>
                <form onSubmit={handleRegister}>
                    <input className="first-name" type="text" placeholder="First Name"
                           onChange={(e) => setFirstName(e.target.value)}/>

                    <input className="last-name" type="text" placeholder="Last Name"
                           onChange={(e) => setLastName(e.target.value)}/>

                    <input className="email" type="email" placeholder="Email"
                           onChange={(e) => setEmail(e.target.value)}/>

                    <input className="username" type="text" placeholder="Username"
                           onChange={(e) => setUsername(e.target.value)}/>

                    <input className="password" type="text" placeholder="Password"
                           onChange={(e) => setPassword(e.target.value)}/>

                    <button className="register-button" type="submit">Register</button>
                    <div style={{marginTop: "10px"}}>
                        <Link to="/login">Already have an account? Log in</Link>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default Register