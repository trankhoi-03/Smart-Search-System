import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import './index.css'
import Login from "./components/Login.jsx";
import Register from "./components/Register.jsx";
import Home from "./components/Home.jsx";
import useToken from './useToken.js'
import ProtectedRoute from "./components/ProtectedRoute.jsx";
import BookDetail from "./components/BookDetail.jsx";

function App() {
    const { token, setToken, removeToken } = useToken();
    
    return(
        <div className="container">
            <BrowserRouter>
                <Routes>
                    <Route path="/login" element={<Login setToken={setToken}/>} />
                    <Route path="/register" element={<Register />}/>
                    <Route
                        path="/"
                        element={!token ? <Login setToken={setToken}/> : <Home removeToken={removeToken}/>}
                    />
                    <Route
                        path="/home"
                        element={
                            <ProtectedRoute token={token}>
                                <Home removeToken={removeToken} token={token}/>
                            </ProtectedRoute>
                        }
                    />
                    <Route
                        path="/book/details"
                        element={
                            <ProtectedRoute token={token}>
                                <BookDetail />
                            </ProtectedRoute>
                        }
                    />
                </Routes>
            </BrowserRouter>
        </div>
    )
}

export default App
