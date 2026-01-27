package com.project.smartsearchsystem.utils;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;

import java.util.Arrays;
import java.util.List;

public class VectorConverter implements AttributeConverter<List<Double>, PGvector> {


    @Override
    public PGvector convertToDatabaseColumn(List<Double> attribute) {
        if (attribute == null) {
            return null;
        }
        // Convert List<Double> to float[] for pgvector
        float[] floatArray = new float[attribute.size()];
        for (int i = 0; i < attribute.size(); i++) {
            floatArray[i] = attribute.get(i).floatValue();
        }
        return new PGvector(floatArray);
    }

    @Override
    public List<Double> convertToEntityAttribute(PGvector dbData) {
        if (dbData == null) {
            return null;
        }
        // Convert float[] from pgvector back to List<Double>
        float[] floatArray = dbData.toArray();
        Double[] doubleArray = new Double[floatArray.length];
        for (int i = 0; i < floatArray.length; i++) {
            doubleArray[i] = (double) floatArray[i];
        }
        return Arrays.asList(doubleArray);
    }
}
