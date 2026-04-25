import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useEffect, useState } from "react";
import BookCard from "../helper/BookCard.jsx";

function BookDetail({ token }) {
    // 1. INITIALIZE HOOKS FIRST (Fixes the ReferenceError!)
    const location = useLocation();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    // 2. INITIALIZE STATE
    const [isAdmin, setIsAdmin] = useState(false);
    const [, setToken] = useState("");
    const [similarBooks, setSimilarBooks] = useState([]);
    const [loadingSimilar, setLoadingSimilar] = useState(false);

    const [book, setBook] = useState(location.state?.book || null);
    const [isLoading, setIsLoading] = useState(!location.state?.book);

    const [aiSummary, setAiSummary] = useState(null);
    const [isGenerating, setIsGenerating] = useState(false);
    const [aiError, setAiError] = useState(null);

    // 3. ALL USE-EFFECTS (Must remain above early returns to obey React rules)
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

    useEffect(() => {
        if (location.state?.book) {
            setBook(location.state.book);
        }
    }, [location.state?.book]);

    useEffect(() => {
        if (book) {
            setIsLoading(false);
            return;
        }

        const titleParam = searchParams.get("title");
        if (!titleParam) {
            navigate("/home");
            return;
        }

        const fetchSharedBook = async () => {
            const storedData = localStorage.getItem("token");
            const token = storedData ? JSON.parse(storedData).token : null;

            try {
                const res = await fetch(`http://localhost:8080/books/search?query=${encodeURIComponent(titleParam)}`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });

                if (res.ok) {
                    const data = await res.json();
                    const foundBook = (data.localResults && data.localResults[0])
                        || (data.googleResults && data.googleResults[0])
                        || (data.openLibraryResults && data.openLibraryResults[0]);

                    if (foundBook) {
                        setBook(foundBook);
                    } else {
                        alert("The shared book could not be found.");
                        navigate("/home");
                    }
                } else {
                    navigate("/home");
                }
            } catch (err) {
                console.error("Failed to fetch shared book", err);
                navigate("/home");
            } finally {
                setIsLoading(false);
            }
        };

        fetchSharedBook();
    }, [book, searchParams, navigate]);

    useEffect(() => {
        if (!book?.title) return;

        const fetchSimilar = async () => {
            setSimilarBooks([]);
            setLoadingSimilar(true);

            const storedData = localStorage.getItem("token");
            const token = storedData ? JSON.parse(storedData).token : null;
            try {
                const params = new URLSearchParams({
                    title: book.title,
                    author: book.author || "",
                    isbn: book.isbn || ""
                });

                const res = await fetch(`http://localhost:8080/api/recommendations/external/similar?${params}`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    }
                });
                if (res.ok) {
                    setSimilarBooks(await res.json());
                }
            } catch (err) {
                console.error("Failed to load similar books", err);
            } finally {
                setLoadingSimilar(false);
            }
        };

        fetchSimilar();
        window.scrollTo(0, 0);
    }, [book]);

    // 4. EARLY RETURNS (Safe to do now that hooks have registered)
    if (isLoading) return <div className="text-white p-8" style={{textAlign: "center"}}>Loading book details...</div>;
    if (!book) return <div className="text-white p-8" style={{textAlign: "center"}}>Book not found.</div>;

    // 5. NORMALIZE DATA
    const title = book.title;
    const author = book.author || book.authors?.join(", ") || "Unknown Author" || "N/A";
    const description = book.description || "No description available for this book.";
    const isbn = book.isbn || "N/A";
    const year = book.publishYear || book.publicationYear || book.publishedDate || "N/A";
    const image = book.image || book.imageUrl || book.coverImageUrl || book.productUrl || "/placeholder.jpg";
    const sourceName = book.source || "Local Library";

    // 🌐 6. SMART EXTERNAL LINK GENERATOR
    const getExternalLink = () => {
        const safeTitle = encodeURIComponent(title);
        const safeAuthor = encodeURIComponent(author !== "Unknown Author" ? author : "");
        const safeIsbn = isbn !== "N/A" ? isbn : null;

        const sourceLower = sourceName.toLowerCase();

        if (sourceLower.includes("amazon")) {
            return `https://www.amazon.com/s?k=${safeIsbn ? safeIsbn : safeTitle + '+' + safeAuthor}`;
        }
        else if (sourceLower.includes("open library")) {
            return `https://openlibrary.org/search?q=${safeIsbn ? safeIsbn : safeTitle}`;
        }
        else if (sourceLower.includes("google")) {
            return `https://www.google.com/search?tbm=bks&q=${safeIsbn ? 'isbn:' + safeIsbn : safeTitle + '+' + safeAuthor}`;
        }

        // Fallback for purely local books without an external source
        return `https://www.google.com/search?q=${safeTitle}+book+by+${safeAuthor}`;
    };

    const externalSourceUrl = getExternalLink();

    const handleInsertBook = async () => { /* ... existing logic ... */ };
    const handleRecommendationClick = (newBook) => {
        navigate(`/book/details?title=${encodeURIComponent(newBook.title)}`, { state: { book: newBook } });
    };

    const handleGenerateSummary = async () => {
        setIsGenerating(true);
        setAiError(null);

        try {
            // Safely encode the title and author for the URL
            const encodedTitle = encodeURIComponent(title);
            const encodedAuthor = encodeURIComponent(author || "N/A");

            const response = await fetch(`http://localhost:8080/books/summary?title=${encodedTitle}&author=${encodedAuthor}`, {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (!response.ok) {
                throw new Error('The AI librarian is currently busy. Please try again.');
            }

            const data = await response.json();
            setAiSummary(data.summary);

        } catch (err) {
            setAiError(err.message);
        } finally {
            setIsGenerating(false);
        }
    };

    // 7. RENDER UI
    return (
        <div className="detail-container">
            <button className="back-btn" onClick={() => navigate(-1)}>← Back to Search</button>

            <div className="detail-content">
                <div className="detail-image-section">
                    <img src={image} alt={title} className="detail-cover"
                         onError={(e) => {e.target.src = "https://placehold.co/300x450?text=No+Cover"}}
                    />
                </div>

                <div className="detail-info-section">
                    <h1 className="detail-title">{title}</h1>
                    {/*<h3 className="detail-author">by {author}</h3>*/}
                    {book.author && book.author !== "N/A" ? (
                        <h3 className="detail-author">by {author}</h3>
                    ) : (
                        <h3 className="detail-author" style={{ fontStyle: 'italic', color: '#888' }}>
                            Author details unavailable
                        </h3>
                    )}

                    <div className="detail-meta">
                        <span className="meta-tag">📅 {year}</span>
                        <span className="meta-tag">ISBN: {isbn}</span>
                    </div>

                    <div className="detail-description">
                        <h3>Description</h3>
                        <p>{description}</p>
                    </div>

                    {/* ✨ AI Summary Section */}
                    <div className="ai-summary-container" style={{ marginTop: '20px', padding: '15px', background: 'rgba(30,30,40,0.8)', borderRadius: '10px', border: '1px solid #444' }}>

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                            <h3 style={{ margin: 0, color: '#e2e8f0' }}>✨ AI Quick Summary</h3>
                            <button
                                onClick={handleGenerateSummary}
                                disabled={isGenerating}
                                style={{
                                    padding: '8px 16px',
                                    background: isGenerating ? '#555' : '#8884d8',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '5px',
                                    cursor: isGenerating ? 'not-allowed' : 'pointer'
                                }}
                            >
                                {isGenerating ? 'Generating...' : 'Generate TL;DR'}
                            </button>
                        </div>

                        {/* Display Errors */}
                        {aiError && <p style={{ color: '#ff7043' }}>{aiError}</p>}

                        {/* Display the Summary */}
                        {aiSummary && (
                            <div style={{ color: '#ccc', lineHeight: '1.6', whiteSpace: 'pre-wrap', fontSize: '0.95rem' }}>
                                {aiSummary}
                            </div>
                        )}
                    </div>

                    {/* 🚀 THE NEW SOURCE BUTTON */}
                    <div className="external-actions" style={{ marginTop: "20px" }}>
                        <a
                            href={externalSourceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                                display: "inline-block",
                                padding: "10px 20px",
                                backgroundColor: "#3b82f6",
                                color: "white",
                                textDecoration: "none",
                                borderRadius: "6px",
                                fontWeight: "bold",
                                transition: "background-color 0.2s"
                            }}
                            onMouseOver={(e) => e.target.style.backgroundColor = "#2563eb"}
                            onMouseOut={(e) => e.target.style.backgroundColor = "#3b82f6"}
                        >
                            🌐 View on {sourceName === "Local Library" ? "Google" : sourceName}
                        </a>
                    </div>

                    {isAdmin && (
                        <div className="admin-actions" style={{marginTop: "15px"}}>
                            <button className="insert-btn" onClick={handleInsertBook}>
                                ➕ Insert Book to Database
                            </button>
                        </div>
                    )}
                </div>
            </div>

            <div className="related-books-section">
                <h3 className="related-books-title">Related Books from Across the Web</h3>
                {loadingSimilar ? (
                    <div className="related-books-loading">
                        {[...Array(5)].map((_, i) => (
                            <div key={i} className="related-books-structure"></div>
                        ))}
                    </div>
                ) : (
                    similarBooks.length > 0 ? (
                        <div className="book-grid">
                            {similarBooks.map((b, index) => (
                                <BookCard key={index} book={b} onClick={() => handleRecommendationClick(b)} />
                            ))}
                        </div>
                    ) : (
                        <p className="related-books-empty">No related external books found.</p>
                    )
                )}
            </div>
        </div>
    );
}

export default BookDetail;