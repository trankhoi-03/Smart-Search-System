package com.project.smartsearchsystem.utils;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER; // We use "OTHER" because 'VECTOR' isn't a standard JDBC type
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    // 1. READ FROM DB (Deserialization) 📖
    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = rs.getString(position); // Read as String: "[0.1, 0.2, ...]"
        if (value == null) {
            return null;
        }
        // Remove brackets and split
        String[] stringFloats = value.replace("[", "").replace("]", "").split(",");
        float[] floats = new float[stringFloats.length];
        for (int i = 0; i < stringFloats.length; i++) {
            floats[i] = Float.parseFloat(stringFloats[i].trim());
        }
        return floats;
    }

    // 2. WRITE TO DB (The "PGobject" fix you found!) ✍️
    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }

        // Create the PGobject manually
        PGobject pgObject = new PGobject();
        pgObject.setType("vector"); // Explicitly tell Postgres: "This is a vector"
        pgObject.setValue(Arrays.toString(value)); // Convert [1.0, 2.0] to string format

        // Send it!
        st.setObject(index, pgObject);
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
