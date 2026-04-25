import {Link, useNavigate} from "react-router-dom";
import { useRef, useEffect, useState} from "react";
import BookList from "../helper/BookList.jsx";
import AIChat from "./AIChat.jsx";
import ImageGrid from "../helper/ImageGrid.jsx";

function Home({ removeToken, token }) {
    const [username, setUsername] = useState("");
    const [isAdmin, setIsAdmin] = useState(false);
    const [,setToken] = useState("");
    const [query, setQuery] = useState("");
    const [results, setResults] = useState(null);
    const [amazonResults, setAmazonResults] = useState(null);
    const [isAmazonLoading, setIsAmazonLoading] = useState(false);
    const [hasSearched, setHasSearched] = useState(false);
    const [history, setHistory] = useState([]);
    const [showHistory, setShowHistory] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [activeTab, setActiveTab] = useState("books")
    const [imageResults, setImageResults] = useState([]);
    const [loadingImages, setLoadingImages] = useState(false);
    const [isListening, setIsListening] = useState(false);
    const fileInputRef = useRef(null)
    const [isAnalyzing, setIsAnalyzing] = useState(false)

    const navigate = useNavigate();

    useEffect(() => {
        const storeData = localStorage.getItem("token");
        if (storeData) {
            const userData = JSON.parse(storeData);
            setToken(userData.token);
            if (userData.role === "ADMIN") {
                setIsAdmin(true);
            }
        }
    }, []);

    const handleLogout = () => {
        sessionStorage.removeItem("searchQuery");
        sessionStorage.removeItem("searchResults");
        removeToken();
        navigate('/login');
    };

    const handleSearch = async (e, voiceQuery = null) => {
        // 1. If an event exists, and it's NOT the Enter key, ignore it.
        if (e && e.key !== 'Enter') return;

        // 2. Determine which query to use
        const finalQuery = voiceQuery || query;

        // 3. If the query is empty, do nothing
        if (!finalQuery.trim()) return;

        // REMOVED the redundant if(e.key === 'Enter') wrapper!
        // From here on, the code just executes normally.

        let authToken = null;
        const storedData = localStorage.getItem("token");

        if (storedData) {
            authToken = JSON.parse(storedData).token;
        }

        if (!authToken) {
            console.error("No token found! Please log in");
            return;
        }

        setShowHistory(false);
        setResults(null);
        setAmazonResults(null);
        setError(null);

        setLoading(true);
        setLoadingImages(true);
        setIsAmazonLoading(true);
        setHasSearched(true);
        setActiveTab("books");

        const fetchImagesBackground = async () => {
            try {
                const imgRes = await fetch(`http://localhost:8080/api/images/search?query=${encodeURIComponent(finalQuery)}`, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${authToken}`,
                        'Content-Type': 'application/json'
                    }
                });

                if (imgRes.ok) {
                    const imgData = await imgRes.json();
                    setImageResults(imgData);
                } else {
                    console.error("Image API returned status:", imgRes.status);
                    setImageResults([]);
                }
            } catch (err) {
                console.error("Failed to fetch images", err);
                setImageResults([]);
            } finally {
                setLoadingImages(false);
            }
        };

        // Fire image fetch in background
        fetchImagesBackground();

        // Proceed with Book fetch
        try {
            const response = await fetch(`http://localhost:8080/books/search?query=${encodeURIComponent(finalQuery)}`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${authToken}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error("Search failed");
            }

            const data = await response.json();
            setResults(data);
            setLoading(false);
            sessionStorage.setItem("searchQuery", finalQuery);
            sessionStorage.setItem("latestSearchResults", JSON.stringify(data));

            const localListSize = data.localResults ? data.localResults.length : 0;

            if (localListSize >= 10) {
                console.log("Local match! Skipping Amazon scrape");
                setIsAmazonLoading(false);
                sessionStorage.removeItem("searchAmazonResults");
                setAmazonResults(null);
                return;
            }

            const amazonResponse = await fetch(`http://localhost:8080/books/search/amazon?query=${encodeURIComponent(finalQuery)}`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${authToken}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!amazonResponse.ok)  {
                throw new Error("Search Amazon failed");
            }
            const amazonData = await amazonResponse.json();
            setAmazonResults(amazonData);
            sessionStorage.setItem("searchAmazonResults", JSON.stringify(amazonData));
        } catch (err) {
            console.error(err);
            setError(err.message);
            setLoading(false);
        } finally {
            setIsAmazonLoading(false);
        }
    };

    const handleVoiceSearch = () => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

        if (!SpeechRecognition) {
            alert("Sorry, your browser doesn't support voice search. Try Google Chrome!")
            return
        }
        const recognition = new SpeechRecognition()
        recognition.lang = 'en-US'
        recognition.interimResults = false
        recognition.maxAlternatives = 1

        recognition.onstart = () => {
            setIsListening(true)
        }

        recognition.onresult = (event) => {
            const transcript = event.results[0][0].transcript

            const cleanTranscript = transcript.replace(/\.$/, "")
            setQuery(cleanTranscript)
            handleSearch(null, cleanTranscript)
        }

        recognition.onerror = (event) => {
            console.error("Voice search error:", event.error)
            setIsListening(false)
        }

        recognition.onend = () => {
            setIsListening(false)
        }
        recognition.start()
    }

    const handleImageUpload = async (e) => {
        const file = e.target.files[0]
        if (!file) return

        setIsAnalyzing(true)
        setQuery("Analyzing image with AI...")

        const formData = new FormData
        formData.append("image", file)

        try {
            const storedData = localStorage.getItem("token")
            const token = storedData ? JSON.parse(storedData).token : null

            const response = await fetch("http://localhost:8080/api/vision/analyze", {
                method: "POST",
                headers: {
                    'Authorization': `Bearer ${token}`
                },
                body: formData // Note: Do NOT set Content-Type manually when sending FormData
            });

            if (response.ok) {
                const extractedText = await response.text()
                setQuery(extractedText)
                handleSearch(null, extractedText)
            }
            else {
                setQuery("")
                alert("AI could not read this image. Please try another one")
            }
        } catch (error) {
            console.error("Vision API Error:", error)
            setQuery("")
        } finally {
            setIsAnalyzing(false)
            e.target.value = null
        }
    }

    const handleBookClick = (book) => {
        const safeTitle = encodeURIComponent(book.title)
        navigate(`/book/details?title=${safeTitle}`, { state: { book } });
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
            const key = book.id || book.isbn || book.title;
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
        // 1. Grab everything from storage
        const savedQuery = sessionStorage.getItem("searchQuery");
        const cachedFastData = sessionStorage.getItem("latestSearchResults");
        const cachedAmazonData = sessionStorage.getItem("searchAmazonResults");

        // 2. If we have a query and fast data, restore the UI!
        if (savedQuery && cachedFastData) {
            setQuery(savedQuery);
            setResults(JSON.parse(cachedFastData));

            // 🚀 FIX: Tell the UI to render the containers!
            setHasSearched(true);

            // 3. Restore Amazon data ONLY if it exists for this query
            if (cachedAmazonData) {
                setAmazonResults(JSON.parse(cachedAmazonData));
            } else {
                setAmazonResults(null);
            }
        }
    }, []);

    return (
        <div className={`home-container ${hasSearched ? "searched" : ""}`}>
            {isAdmin && (
                <Link to="/admin/dashboard" style={{ color: 'white', background: '#333', padding: '10px', borderRadius: '5px' }}>
                    Go to Admin Dashboard
                </Link>
            )}
            <div className="top-nav">
                <div className="welcome-pill">
                    <span className="welcome-text">Welcome, <strong>{username}</strong></span>
                    <button className="logout-button" onClick={handleLogout}>Log out</button>
                </div>
            </div>

            {/* Search Section */}
            <div className="search-section">
                <h1 className="logo-text">Search for your books</h1>
                {!hasSearched && (
                    <p className="hero-subtitle">Discover millions of titles from your local library, Amazon, and across
                        the web.</p>
                )}


                <div className="search-input-wrapper">
                    {/*SVG Search Icon*/}
                    <svg className="search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="11" cy="11" r="8"></circle>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>

                    <input
                        type="search"
                        className="search-bar"
                        placeholder="What do you want to read next?"
                        value={query}
                        onChange={(e) => {
                            setQuery(e.target.value)
                            setShowHistory(true)
                        }}
                        onKeyDown={handleSearch}
                        onFocus={handleDisplayHistory}
                        onBlur={() => setTimeout(() => setShowHistory(false), 200)}
                    />

                    <button
                        type="button"
                        className={`mic-button ${isListening ? 'listening' : ''}`}
                        onClick={handleVoiceSearch}
                        title="Search by voice"
                    >
                        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
                            <path d="M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"/>
                        </svg>
                    </button>

                    <input
                        type="file"
                        accept="image/*"
                        ref={fileInputRef}
                        style={{ display: 'none' }}
                        onChange={handleImageUpload}
                    />

                    <button
                        type="button"
                        className={`camera-button ${isAnalyzing ? 'analyzing' : ''}`}
                        onClick={() => fileInputRef.current.click()}
                        title="Search by Image"
                        disabled={isAnalyzing}
                    >
                        <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                            <circle cx="12" cy="12" r="3.2"/>
                            <path
                                d="M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"/>
                        </svg>
                    </button>

                    {showHistory && history.length > 0 && (
                        <ul className="search-dropdown">
                            {history.length > 0 ? (
                                history.map((suggestion, index) => (
                                    <li
                                        key={index}
                                        onClick={() => handleHistoryClick(suggestion)}
                                        className="dropdown-item"
                                        onMouseDown={(e) => e.preventDefault()}
                                    >
                                        <svg className="history-icon" xmlns="http://www.w3.org/2000/svg" width="14"
                                             height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                             strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                            <circle cx="12" cy="12" r="10"></circle>
                                            <polyline points="12 6 12 12 16 14"></polyline>
                                        </svg>
                                        {suggestion}
                                    </li>
                                ))
                            ) : (
                                <li className="dropdown-empty">No search history available</li>
                            )}
                        </ul>
                    )}
                </div>

                {hasSearched && (
                    <div className="search-tabs-container">
                        <button
                            className={`tab-btn ${activeTab === 'books' ? 'active' : ''}`}
                            onClick={() => setActiveTab('books')}
                        >
                            📚 Books
                        </button>
                        <button
                            className={`tab-btn ${activeTab === 'images' ? 'active' : ''}`}
                            onClick={() => setActiveTab('images')}
                        >
                            🖼️ Images
                        </button>
                    </div>
                )}
            </div>

            {/* Results Section */}
            {hasSearched && (
                <div className="results-container">
                    {loading && (
                        <div className="global-loading">
                            <div className="spinner"></div>
                            <p>Scouring the web for your {activeTab}...</p>
                        </div>
                    )}
                    {error && <p className="error-text">{error}</p>}

                    {!loading && (
                        <>
                            {/* 4 Clean Sections – Horizontal Grid, No Sub-Split */}
                            {activeTab === "books" && results && (
                                <>
                                    {/* 1. Local Library */}
                                    <BookList
                                        title="Local Library"
                                        books={results.localResults || []}
                                        onClick={handleBookClick}
                                    />
                                    {/* 2. Google Books */}
                                    <BookList
                                        title="Google Books"
                                        books={results.googleBookResults || []}
                                        onClick={handleBookClick}
                                    />
                                    {/* 3. Open Library */}
                                    <BookList
                                        title="Open Library"
                                        books={results.openLibraryBookResults || []}
                                        onClick={handleBookClick}
                                    />
                                    <>
                                        <h3 className="amazon-title">{isAmazonLoading ? 'Amazon Recommendation' : null}</h3>

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
                                            <p className="amazon-empty-message">No Amazon results found for this
                                                query.</p>
                                        )}
                                    </>
                                </>
                            )}
                            {activeTab === "images" && (
                                <div className="source-section">
                                    <h3 className="source-title">Image Results</h3>
                                    {loadingImages ? (
                                        <div className="global-loading">
                                            <div className="spinner"></div>
                                            <p>Loading high-quality images...</p>
                                        </div>
                                    ) : (
                                        <ImageGrid images={imageResults}/>
                                    )}
                                </div>
                            )}
                        </>
                    )}
                </div>
            )}
            <AIChat/>
        </div>
    );
}

export default Home;