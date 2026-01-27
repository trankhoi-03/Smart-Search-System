import { useNavigate, Link } from "react-router-dom";
import {useEffect, useState} from "react";
import BookCard from "../helper/BookCard.jsx";
import BookList from "../helper/BookList.jsx";

function Home({ removeToken, token }) {
    const [username, setUsername] = useState("")
    const [query, setQuery] = useState("");
    const [results, setResults] = useState(null) // Store the API response
    const [hasSearched, setHasSearched] = useState(false) // Triggers the animation
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const navigate = useNavigate()

    const handleLogout = () => {
        sessionStorage.removeItem("searchQuery")
        sessionStorage.removeItem("searchResults")
        removeToken()
        navigate('/login')
    }

    const handleSearch = async (e) => {
        if (e.key === 'Enter') {
            if (!query.trim()) return

            let authToken = null
            const storedData = localStorage.getItem("token")

            if (storedData) {
                authToken = JSON.parse(storedData).token
            }

            if (!authToken) {
                console.error("No token found! Please log in")
                return
            }

            setLoading(true)
            setHasSearched(true)
            setError(null)

            try {
                // Call backend API
                const response = await fetch(`http://localhost:8080/books/search?query=${query}`, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${authToken}`,
                        'Content-Type':  'application/json'
                    }
                })
                if (!response.ok) {
                    throw new Error("Search failed")
                }
                const data = await response.json()
                setResults(data)
                sessionStorage.setItem("searchQuery", query)
                sessionStorage.setItem("searchResults", JSON.stringify(data))
            } catch (err) {
                console.error(err)
            } finally {
                setLoading(false)
            }
        }
    }

    const handleBookClick = (book) => {
        navigate('/book/details', { state: { book: book } })
    }

    useEffect(() => {
        const storedData = localStorage.getItem("token")
        if (storedData) {
            const userData = JSON.parse(storedData)

            setUsername(userData.username || "User")
        }

    }, []);

    useEffect(() => {
        // Check if we have saved data
        const savedQuery = sessionStorage.getItem("searchQuery")
        const savedResults = sessionStorage.getItem("searchResults")

        if (savedQuery && savedResults) {
            setQuery(savedQuery)
            setResults(JSON.parse(savedResults))
            setHasSearched(true)
        }
    }, []);

    return (
        <div className={`home-container ${hasSearched ? "searched" : ""}`}>
            <div className="top-nav">
                <div className="welcome-text">
                    Welcome, <strong>{username}</strong>
                </div>
                <button className="logout-button" onClick={handleLogout}>Log out</button>
            </div>

            {/* Search Section (This will slide up) */}
            <div className="search-section">
                <h1 className="logo-text">Search for your books</h1>
                <div className="search-input-wrapper">
                    <input
                        type="text"
                        className="search-bar"
                        placeholder="Enter title"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        onKeyDown={handleSearch}
                    />
                </div>
            </div>

            {/* Results Section (Appears only after search) */}
            {hasSearched && (
                <div className="results-container">
                    {loading && <p className="loading-text">Searching...</p>}
                    {error && <p className="error-text">{error}</p>}

                    {!loading && results && (
                        <>
                            {/* 1. HANDLE EXACT MATCH (Single Result) 🎯 */}
                            {results.single && results.singleResult && (
                                <div className="source-section">
                                    <h3 className="source-title" style={{color: '#ffeb3b'}}>🌟 Exact Match Found!</h3>
                                    <div className="book-grid">
                                        {/* ✅ Loop through the exact matches */}
                                        {Array.isArray(results.singleResult) ? (
                                            results.singleResult.map((book, index) => (
                                                <BookCard key={index} book={book} onClick={() => handleBookClick(book)}/>
                                            ))
                                        ) : (
                                            // Fall back in case backend sends a single object
                                            <BookCard book={results.singleResult} onClick={() => handleBookClick(results.singleResult)}/>
                                        )}
                                    </div>
                                </div>
                            )}

                            {/* 2. HANDLE REGULAR SEARCH LISTS (Use the correct keys!) 📚 */}
                            {/* Only show these if it's NOT a single match, or if you want to show both */}
                            {!results.single && (
                                <>
                                    <BookList title="Local Library" books={results.localResults} onClick={handleBookClick} />
                                    <BookList title="Google Books" books={results.googleBookResults} onClick={handleBookClick} />
                                    <BookList title="Open Library" books={results.openLibraryBookResults} onClick={handleBookClick} />
                                    <BookList title="Amazon" books={results.amazonBookResults} onClick={handleBookClick} />
                                </>
                            )}
                        </>
                    )}
                </div>
            )}
        </div>
    )
}



export default Home
