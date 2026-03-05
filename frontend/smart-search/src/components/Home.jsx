import { useNavigate, Link } from "react-router-dom";
import {use, useEffect, useState} from "react";
import BookCard from "../helper/BookCard.jsx";
import BookList from "../helper/BookList.jsx";
import AIChat from "./AIChat.jsx";

function Home({ removeToken, token }) {
    const [username, setUsername] = useState("");
    const [query, setQuery] = useState("");
    const [results, setResults] = useState(null);
    const [amazonResults, setAmazonResults] = useState(null);
    const [isAmazonLoading, setIsAmazonLoading] = useState(false);
    const [hasSearched, setHasSearched] = useState(false);
    const [history, setHistory] = useState([]);
    const [showHistory, setShowHistory] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const navigate = useNavigate();

    const handleLogout = () => {
        sessionStorage.removeItem("searchQuery");
        sessionStorage.removeItem("searchResults");
        removeToken();
        navigate('/login');
    };

    const handleSearch = async (e) => {
        if (e.key === 'Enter') {
            if (!query.trim()) return;

            let authToken = null;
            const storedData = localStorage.getItem("token");

            if (storedData) {
                authToken = JSON.parse(storedData).token;
            }

            if (!authToken) {
                console.error("No token found! Please log in");
                return;
            }

            setShowHistory(false)
            setResults(null)
            setAmazonResults(null)
            setError(null)

            setLoading(true);
            setIsAmazonLoading(true)
            setHasSearched(true);
            setError(null);

            try {
                // Step 1: Fast Search (Local + Google + Open Library)
                const response = await fetch(`http://localhost:8080/books/search?query=${encodeURIComponent(query)}`, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${authToken}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (!response.ok) {
                    throw new Error("Search failed")
                }

                const data = await response.json()
                setResults(data)
                setLoading(false)

                // Step 2: Amazon Search
                const amazonResponse = await fetch(`http://localhost:8080/books/search/amazon?query=${encodeURIComponent(query)}`, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${authToken}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (!amazonResponse.ok)  {
                    throw new Error("Search Amazon failed")
                }
                const amazonData = await amazonResponse.json()
                setAmazonResults(amazonData)
                sessionStorage.setItem("searchQuery", query);
                sessionStorage.setItem("searchResults", JSON.stringify(data));
                sessionStorage.setItem("searchAmazonResults", JSON.stringify(amazonData))
            } catch (err) {
                console.error(err);
                setError(err.message);
                setLoading(false)
            } finally {
                setIsAmazonLoading(false)
            }
        }
    };

    const handleBookClick = (book) => {
        navigate('/book/details', { state: { book } });
    };

    const handleDisplayHistory = async () => {
        setShowHistory(true)

        const storedData = localStorage.getItem("token")
        if (!storedData) return

        try {
            const res = await fetch('http://localhost:8080/books/most-search', {
                headers: {
                    'Authorization': `Bearer ${JSON.parse(storedData).token}`
                }
            })
            const data = await res.json()
            setHistory(data)
        } catch (err) {
            console.error(err)
        }
    }

    const handleHistoryClick = (pastQuery) => {
        setQuery(pastQuery)
        setShowHistory(false);

        handleSearch({ preventDefault: () => {} })
    }

    // Helper to deduplicate books by id or title (safe)
    // Quick fix for your deduplicate function if books have no IDs
    const deduplicate = (books) => {
        if (!books) return [];
        const unique = new Map();
        books.forEach(book => {
            // Use ASIN/ISBN if available, otherwise fallback to Title
            const key = book.isbn || book.asin;
            if (!unique.has(key)) {
                unique.set(key, book);
            }
        });
        return Array.from(unique.values());
    };

    useEffect(() => {
        const storedData = localStorage.getItem("token");
        if (storedData) {
            const userData = JSON.parse(storedData);
            setUsername(userData.username || "User");
        }
    }, []);

    useEffect(() => {
        const savedQuery = sessionStorage.getItem("searchQuery");
        const savedResults = sessionStorage.getItem("searchResults");
        const saveAmazonResults = sessionStorage.getItem("searchAmazonResults")

        if (savedQuery && savedResults && saveAmazonResults) {
            setQuery(savedQuery);
            setResults(JSON.parse(savedResults));
            setAmazonResults(JSON.parse(saveAmazonResults))
            setHasSearched(true);
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

            {/* Search Section */}
            <div className="search-section">
                <h1 className="logo-text">Search for your books</h1>
                <div className="search-input-wrapper">
                    <input
                        type="search"
                        className="search-bar"
                        placeholder="Search for your books"
                        value={query}
                        onChange={(e) => {
                            setQuery(e.target.value)
                            setShowHistory(true)
                        }}
                        onKeyDown={handleSearch}
                        onFocus={handleDisplayHistory}
                        onBlur={() => setTimeout(() => setShowHistory(false), 200)}
                    />

                    {showHistory && history.length > 0 && (
                        <ul className="search-dropdown">
                            {history.length > 0 ? (
                                history.map((suggestion, index) => (
                                    <li
                                        key={index}
                                        onClick={() => handleHistoryClick(suggestion)}
                                        style={{
                                            padding: "12px 16px",
                                            cursor: "pointer",
                                            borderBottom: index < history.length - 1 ? "1px solid #374151" : "none",
                                            display: "flex",
                                            alignItems: "center"
                                        }}
                                        onMouseDown={(e) => e.preventDefault()}
                                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = "#374151"}
                                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = "transparent"}
                                    >
                                        {suggestion}
                                    </li>
                                ))
                            ) : (
                                <li style={{padding: "12px 16px", color: "#9ca3af"}}>
                                    No search history available
                                </li>
                            )}
                        </ul>
                    )}
                </div>
            </div>

            {/* Results Section */}
            {hasSearched && (
                <div className="results-container">
                    {loading && <p className="loading-text">Searching...</p>}
                    {error && <p className="error-text">{error}</p>}

                    {!loading && results && (
                        <>
                            {/* Exact Match Banner */}
                            {results.single && results.singleResult && (
                                <div className="source-section">
                                    <h3 className="source-title" style={{ color: '#ffeb3b' }}>
                                        🌟 Exact Match Found!
                                    </h3>
                                    <div className="book-grid">
                                        {Array.isArray(results.singleResult) ? (
                                            results.singleResult.map((book, index) => (
                                                <BookCard
                                                    key={index}
                                                    book={book}
                                                    onClick={() => handleBookClick(book)}
                                                />
                                            ))
                                        ) : (
                                            <BookCard
                                                book={results.singleResult}
                                                onClick={() => handleBookClick(results.singleResult)}
                                            />
                                        )}
                                    </div>
                                </div>
                            )}

                            {/* 4 Clean Sections – Horizontal Grid, No Sub-Split */}
                            {!results.single && (
                                <>
                                    {/* 1. Local Library */}
                                    <BookList
                                        title="Local Library"
                                        books={deduplicate([
                                            ...(results.localKeywordResults || []),
                                            ...(results.localSemanticResults || [])
                                        ])}
                                        onClick={handleBookClick}
                                    />
                                    {/* 2. Google Books */}
                                    <BookList
                                        title="Google Books"
                                        books={deduplicate([
                                            ...(results.googleKeywordResults || []),
                                            ...(results.googleSemanticResults || [])
                                        ])}
                                        onClick={handleBookClick}
                                    />
                                    {/* 3. Open Library */}
                                    <BookList
                                        title="Open Library"
                                        books={deduplicate([
                                            ...(results.openLibraryKeywordResults || []),
                                            ...(results.openLibrarySemanticResults || [])
                                        ])}
                                        onClick={handleBookClick}
                                    />
                                    <>
                                        <h3 className="amazon-title">Amazon Recommendations</h3>

                                        {/* State A: Loading - Show specific spinner while user reads other results */}
                                        {isAmazonLoading && (
                                            <div className="amazon-loading-container">
                                                <div className="amazon-loading-spinner"></div>
                                                <p>Searching Amazon's bestsellers...</p>
                                            </div>
                                        )}

                                        {/* State B: Results Loaded - Render the list */}
                                        {!isAmazonLoading && amazonResults && amazonResults.length > 0 && (
                                            <BookList
                                                title="Amazon" // Title is already handled above to stay visible during load
                                                books={deduplicate(amazonResults)} // Use the new independent state
                                                onClick={handleBookClick}
                                            />
                                        )}

                                        {/* State C: Loaded but Empty - Optional helpful message */}
                                        {!isAmazonLoading && amazonResults && amazonResults.length === 0 && (
                                            <p className="amazon-empty-message">No Amazon results found for this query.</p>
                                        )}
                                    </>
                                </>
                            )}
                        </>
                    )}
                </div>
            )}
            <AIChat />
        </div>
    );
}

export default Home;