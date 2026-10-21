package com.bookstore.ecommerce.api;

import com.bookstore.ecommerce.service.AvatarService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves stored profile pictures. Names are random and never reused, so responses can be cached for
 * good. nosniff and a locked-down CSP make sure a browser only ever treats them as images.
 */
@RestController
public class MediaController {

    private final AvatarService avatarService;

    public MediaController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/media/avatars/{name:[a-f0-9]{32}\\.(?:jpg|png)}")
    public ResponseEntity<byte[]> avatar(@PathVariable String name) {
        return avatarService.load("avatars/" + name)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(img.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
