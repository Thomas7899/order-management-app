import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ImageService {
  private apiUrl = 'http://localhost:8080/api/images';

  constructor(private http: HttpClient) {}

  getImageDataUrl(imagePath: string): Observable<string> {
    // Extrahiere Dateiname aus dem Pfad (/images/laptop.jpg -> laptop.jpg)
    const filename = imagePath.replace('/images/', '');
    
    return this.http.get<{dataUrl: string}>(`${this.apiUrl}/${filename}`)
      .pipe(
        map(response => response.dataUrl)
      );
  }
}