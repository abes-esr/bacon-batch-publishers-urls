package fr.abes.bacon.baconediteurs.batch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Service
@Slf4j
public class DownloadService {
    private final RestTemplate restTemplate;

    public DownloadService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<byte[]> getRestCall(String url) throws RestClientException {
        log.debug("Appel de l'URL {}", url);
        return restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
        log.debug("GET : " + url);
        // En-têtes pour simuler un navigateur
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/114.0.0.0 Safari/537.36");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Construction propre de l'URI (pas de double-encoding)
        URI uri = UriComponentsBuilder
                .fromHttpUrl(url)
                .build(true)
                .toUri();

        return restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
    }
}
