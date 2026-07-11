package com.policescheduler.repository;

import com.policescheduler.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    List<Translation> findByLocale(String locale);

    Optional<Translation> findByTranslationKeyAndLocale(String translationKey, String locale);

    List<Translation> findByTranslationKeyIn(List<String> translationKeys);
}
