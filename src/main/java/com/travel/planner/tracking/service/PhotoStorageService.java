package com.travel.planner.tracking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 기록 사진을 서버 로컬 파일로 저장하고 공개 URL 경로("/uploads/{name}")를 돌려준다.
 * 정적 서빙은 WebConfig 의 /uploads/** 매핑이 담당.
 */
@Service
public class PhotoStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    /** 예약 확인증 등 문서: 이미지 + PDF 허용. */
    private static final Set<String> DOC_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".pdf");

    private final Path root;

    public PhotoStorageService(@Value("${app.upload-dir:./uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉터리 생성 실패: " + root, e);
        }
    }

    /** 사진 저장. file 이 비었으면 null. 이미지가 아니면 예외. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 첨부할 수 있습니다.");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + extensionOf(file.getOriginalFilename());
        try {
            file.transferTo(root.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("사진 저장 실패: " + e.getMessage(), e);
        }
        return "/uploads/" + name;
    }

    /**
     * 예약 확인증 등 문서 저장. 이미지 + PDF 허용. 파일이 비었으면 null.
     */
    public String storeDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType();
        boolean ok = contentType != null
                && (contentType.startsWith("image/") || contentType.equals("application/pdf"));
        if (!ok) {
            throw new IllegalArgumentException("이미지 또는 PDF 파일만 첨부할 수 있습니다.");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + docExtensionOf(file.getOriginalFilename());
        try {
            file.transferTo(root.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패: " + e.getMessage(), e);
        }
        return "/uploads/" + name;
    }

    /** 저장된 파일 삭제. URL이 아니거나 경로 이탈이면 무시. (사진·문서 공용) */
    public void delete(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith("/uploads/")) {
            return;
        }
        Path target = root.resolve(photoUrl.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root)) {
            return; // 경로 이탈 방지
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 파일 삭제 실패는 치명적이지 않음 — 레코드 삭제는 진행
        }
    }

    private static String docExtensionOf(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                String ext = filename.substring(dot).toLowerCase();
                if (DOC_EXT.contains(ext)) {
                    return ext;
                }
            }
        }
        return ".jpg";
    }

    private static String extensionOf(String filename) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                String ext = filename.substring(dot).toLowerCase();
                if (ALLOWED_EXT.contains(ext)) {
                    return ext;
                }
            }
        }
        return ".jpg";
    }
}
