import { useLocation, useNavigate } from "react-router-dom";
import {useEffect, useState} from "react";

function BookDetail() {
    const [isAdmin, setIsAdmin] = useState(false)
    const [token, setToken] = useState("")
    const location = useLocation();
    const navigate = useNavigate();

    // Retrieve the book object passed from the previous page
    const book = location.state?.book;

    useEffect(() => {
        const storeData = localStorage.getItem("token")
        if (storeData) {
            const userData = JSON.parse(storeData)
            setToken(userData.token)
            if (userData.role === "ADMIN") {
                setIsAdmin(true)
            }
        }
    }, []);

    if (!book) return null

    // If someone tries to go to this URL directly without clicking a book, send them Home
    if (!book) {
        return (
            <div className="detail-container">
                <h2>No book selected</h2>
                <button onClick={() => navigate("/home")}>Go Back Home</button>
            </div>
        );
    }

    // Normalize data (Handling different field names from different APIs)
    const title = book.title;
    const author = book.author || book.authors?.join(", ") || "Unknown Author";
    const description = book.description || "No description available for this book.";
    const isbn = book.isbn || "N/A";
    const year = book.publishYear || book.publicationYear || book.publishedDate || "N/A";
    const image = book.image || book.imageUrl || book.coverImageUrl || book.productUrl || "/placeholder.jpg";

    const handleInsertBook = async () => {
        if (!confirm(`Are you sure you want to add "${title}" to the database?`)) return

        try {
            const response = await fetch("http://localhost:8080/books/add", {
                method: 'PUT',
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    title: title,
                    author: author,
                    description: description.substring(0, 2499),
                    isbn: isbn,
                    publicationYear: year.toString(),
                    imageUrl: image
                })
            })

            if (response.ok) {
                alert("Book added successfully!")
                console.log(response)
            }
            else {
                const errorMessage = await response.text()
                alert(`Failed to add book: ${errorMessage}`)
            }
        } catch (error) {
            console.error(error)
            alert("Network error occurred")
        }
    }

    return (
        <div className="detail-container">
            <button className="back-btn" onClick={() => navigate(-1)}>← Back to Search</button>

            <div className="detail-content">
                {/* Left Side: Image */}
                <div className="detail-image-section">
                    <img src={image} alt={title} className="detail-cover"
                         onError={(e) => {e.target.src = "https://placehold.co/300x450?text=No+Cover"}}
                    />
                </div>

                {/* Right Side: Info */}
                <div className="detail-info-section">
                    <h1 className="detail-title">{title}</h1>
                    <h3 className="detail-author">by {author}</h3>

                    <div className="detail-meta">
                        <span className="meta-tag">📅 {year}</span>
                        <span className="meta-tag">ISBN: {isbn}</span>
                    </div>

                    <div className="detail-description">
                        <h3>Description</h3>
                        <p>{description}</p>
                    </div>

                    {isAdmin && (
                        <div className="admin-actions" style={{marginTop: "30px"}}>
                            <button
                                className="insert-btn"
                                onClick={handleInsertBook}
                            >
                                ➕ Insert Book to Database
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default BookDetail;