package com.bryanstrk.pulser.evento;

import com.bryanstrk.pulser.shared.exception.BadRequestException;
import com.bryanstrk.pulser.shared.exception.ExternalServiceException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class ImageStorageService {

    // Coherente con spring.servlet.multipart.max-file-size = 5MB del application.yaml.
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES_PERMITIDOS =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final Cloudinary cloudinary;

    public ImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Valida y sube la imagen a Cloudinary con un public_id determinista (overwrite en el sitio).
     * @return secure_url del asset.
     */
    public String subir(MultipartFile file, String publicId) {
        validar(file);
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "image",
                            "overwrite", true
                    ));
            return (String) resultado.get("secure_url");
        } catch (IOException | RuntimeException e) {
            // Fallo de red/IO o cualquier error del SDK -> 503, sin filtrar detalles internos.
            throw new ExternalServiceException("Error subiendo imagen a Cloudinary", e);
        }
    }

    private void validar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo esta vacio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("El archivo supera el tamaño maximo permitido (5MB)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            throw new BadRequestException("Tipo de archivo no permitido; usa JPEG, PNG o WEBP");
        }
    }
}
