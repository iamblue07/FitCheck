package com.fitcheck.common.storage;

import com.fitcheck.common.exception.ExternalServiceException;

import java.net.URI;
import java.time.Duration;

/**
 * Provider-agnostic access to object storage. Nothing in this contract should reference a
 * specific provider's types — this project currently backs it with Cloudflare R2 (see
 * {@link R2Config}), but swapping providers should stay a one-class change.
 */
public interface StorageService {

    /**
     * Default lifetime for presigned URLs returned by this service. Kept short — a long-lived
     * presigned URL is effectively a public link to private data.
     */
    Duration DEFAULT_TTL = Duration.ofMinutes(15);

    /**
     * Generates a presigned URL the client can PUT bytes to directly, without those bytes
     * passing through the backend.
     *
     * @param key         the destination object key
     * @param contentType the exact content type the upload must match — pinned at signing time
     *                    so the URL can't be reused to upload a different file type
     * @param ttl         how long the URL remains valid
     * @return a presigned PUT URL
     * @throws ExternalServiceException if the URL can't be generated
     */
    URI generateUploadUrl(String key, String contentType, Duration ttl);

    /**
     * Generates a presigned URL for reading an existing object.
     *
     * @param key the object key to read
     * @param ttl how long the URL remains valid
     * @return a presigned GET URL
     * @throws ExternalServiceException if the URL can't be generated
     */
    URI generateDownloadUrl(String key, Duration ttl);

    /**
     * Uploads bytes already held in memory — used when the backend itself produces the content
     * (e.g. a result downloaded from an external API), rather than a client uploading directly.
     *
     * @param key         the destination object key
     * @param bytes       the object content
     * @param contentType the content type to store the object as
     * @throws ExternalServiceException if the upload fails
     */
    void store(String key, byte[] bytes, String contentType);

    /**
     * Checks whether an object actually exists at the given key. Used to verify a client-side
     * upload really landed, rather than trusting the client's word for it.
     *
     * @param key the object key to check
     * @return true if an object exists at that key
     * @throws ExternalServiceException if the check fails
     */
    boolean exists(String key);

    /**
     * Deletes an object.
     *
     * @param key the object key to remove
     * @return true if an object existed and was removed; false if the key was already absent
     * @throws ExternalServiceException if the deletion fails
     */
    boolean delete(String key);
}