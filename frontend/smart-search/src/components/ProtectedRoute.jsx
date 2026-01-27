import {Navigate} from "react-router-dom";

function ProtectedRoute({ children, token }) {
    if (!token) {
        return <Navigate to="/login" replace />
    }
    return children
}

export default ProtectedRoute