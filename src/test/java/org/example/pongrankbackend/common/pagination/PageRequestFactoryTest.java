package org.example.pongrankbackend.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestFactoryTest {

    private final Sort byName = Sort.by("name").ascending();

    @Test
    @DisplayName("of: respeta valores válidos y el orden indicado")
    void of_ValidValues() {
        Pageable pageable = PageRequestFactory.of(2, 20, byName);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(byName);
    }

    @Test
    @DisplayName("of: limita el tamaño máximo de página a MAX_SIZE")
    void of_SizeAboveMaximum_IsCapped() {
        assertThat(PageRequestFactory.of(0, 1000, byName).getPageSize()).isEqualTo(PageRequestFactory.MAX_SIZE);
    }

    @Test
    @DisplayName("of: corrige página negativa y tamaño menor que 1")
    void of_InvalidValues_AreAdjusted() {
        Pageable pageable = PageRequestFactory.of(-3, 0, byName);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("PageResponseDTO.from: copia contenido convertido y metadatos de la página")
    void pageResponse_FromPage() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponseDTO<String> dto = PageResponseDTO.from(page, n -> "n" + n);

        assertThat(dto.getContent()).containsExactly("n1", "n2");
        assertThat(dto.getPage()).isEqualTo(1);
        assertThat(dto.getSize()).isEqualTo(2);
        assertThat(dto.getTotalElements()).isEqualTo(5);
        assertThat(dto.getTotalPages()).isEqualTo(3);
        assertThat(dto.isFirst()).isFalse();
        assertThat(dto.isLast()).isFalse();
    }
}
