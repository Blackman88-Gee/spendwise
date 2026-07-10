package com.spendwise.dao;

import com.spendwise.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    void delete(int id);

    List<Category> findAll();

    Optional<Category> findById(int id);
}
