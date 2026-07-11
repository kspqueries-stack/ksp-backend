package com.policescheduler.repository;

import com.policescheduler.entity.PdfUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfUploadRepository extends JpaRepository<PdfUpload, Long> {

    List<PdfUpload> findByUserId(Long userId);
}
