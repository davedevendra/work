package pl.finsys.example.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.finsys.example.domain.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

}
