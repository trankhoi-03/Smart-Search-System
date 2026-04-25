import React, { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import {useNavigate} from "react-router-dom";


function AdminDashboard({ token }) {
    const [stats, setStats] = useState({ topSearches: [], missedInventory: [] });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const navigate = useNavigate();


    useEffect(() => {
        if (token) {
            fetchDashboardData();
        }
    }, [token]);

    const fetchDashboardData = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/admin/dashboard/stats', {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    // 🚀 Use the passed token directly
                    'Authorization': `Bearer ${token}`
                }
            });

            if (response.status === 403) {
                throw new Error('Access Denied: You do not have Admin privileges.');
            }

            if (!response.ok) {
                throw new Error('Failed to fetch dashboard data.');
            }

            const data = await response.json();
            setStats(data);
            setLoading(false);
        } catch (err) {
            setError(err.message);
            setLoading(false);
        }
    };

    if (loading) return <div className="admin-loading">Loading Dashboard Data...</div>;
    if (error) return <div className="admin-error">Error: {error}</div>;

    return (
        <div className="admin-dashboard-container">
            <button className="back-btn" onClick={() => navigate(-1)}>← Back to Search</button>
            <h1 className="dashboard-title">Librarian Analytics Dashboard</h1>

            <div className="charts-grid">
                {/* CHART 1: Global Top Searches */}
                <div className="chart-card">
                    <h2>🔥 Most Searched Topics</h2>
                    <p>What your users are looking for overall.</p>
                    <div className="chart-wrapper">
                        <ResponsiveContainer width="100%" height={400}>
                            {/* 🚀 Add layout="vertical" */}
                            <BarChart layout="vertical" data={stats.topSearches} margin={{ top: 20, right: 30, left: 160, bottom: 5 }}>
                                {/* Turn off horizontal grid lines, turn on vertical */}
                                <CartesianGrid strokeDasharray="3 3" stroke="#444" horizontal={false} vertical={true} />

                                {/* 🚀 X is now the Number, Y is now the Category */}
                                <XAxis type="number" stroke="#ccc" />
                                <YAxis
                                    type="category"
                                    dataKey="query"
                                    stroke="#ccc"
                                    width={150} // Gives the long text plenty of room
                                    tick={{ fontSize: 12 }}
                                    interval={0}
                                />

                                <Tooltip contentStyle={{ backgroundColor: '#222', borderColor: '#444' }} />
                                <Legend />
                                <Bar dataKey="count" name="Search Volume" fill="#8884d8" radius={[0, 4, 4, 0]} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* CHART 2: Missed Inventory (The Actionable Data!) */}
                <div className="chart-card">
                    <h2>🚨 Missed Inventory Alerts</h2>
                    <p>Topics searched that yielded 0 local results. Time to buy these books!</p>
                    <div className="chart-wrapper">
                        <ResponsiveContainer width="100%" height={300}>
                            <BarChart data={stats.missedInventory} margin={{top: 20, right: 30, left: 20, bottom: 5}}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#444"/>
                                <XAxis dataKey="query" stroke="#ccc"/>
                                <YAxis stroke="#ccc"/>
                                <Tooltip contentStyle={{backgroundColor: '#222', borderColor: '#444'}}/>
                                <Legend/>
                                <Bar dataKey="count" name="Failed Local Searches" fill="#ff7043" radius={[4, 4, 0, 0]}/>
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default AdminDashboard