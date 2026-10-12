import { OrderRequestDetail, draftFrom, senderName } from './order-requests.service';

function detail(): OrderRequestDetail {
  return {
    id: 7, requestNumber: '70000007', status: { code: 'OPEN', text: 'Offen' }, sourceType: { code: 'EMAIL', text: 'E-Mail' },
    sender: 'Greta Gärtner <greta@example.com>', subject: 'Bestellung', receivedAt: '2026-10-12T10:00:00', sourceText: '…',
    fileName: null, extractionSource: 'KI', extractedAt: '2026-10-12T10:00:05',
    customer: {
      extracted: { name: 'Greta Gärtner', company: null, email: 'greta@example.com', customerNumber: null, city: null },
      match: { customerId: 3, customerNumber: '100003', name: 'Greta Gärtner', email: 'greta@example.com', city: 'Augsburg',
        score: 0.98, confidence: 'HIGH', reason: 'E-Mail-Adresse bekannt' },
      alternatives: []
    },
    customerReference: 'B-1', requestedDeliveryDate: '2026-10-16', deliveryAddress: null, notes: 'Bitte anrufen',
    items: [
      {
        lineNumber: 10, extracted: { articleNumber: '200011', description: 'IKEA Beleuchtung', quantity: 4, unit: 'Stück', catalogMaterialNumber: null },
        match: { productId: 11, materialNumber: '200011', name: 'IKEA TRÅDFRI', unitOfMeasure: 'ST', price: 400, score: 1, confidence: 'HIGH', reason: 'Materialnummer' },
        alternatives: []
      },
      {
        lineNumber: 20, extracted: { articleNumber: null, description: 'Druckerpapier A4', quantity: null, unit: 'Karton', catalogMaterialNumber: null },
        match: null, alternatives: []
      }
    ],
    confidence: 'NONE', messages: [], orderId: null, orderNumber: null, rejectionNote: null,
    processedAt: null, processedBy: null, createdBy: 'MREUTER'
  };
}

describe('Auftragseingang', () => {
  it('übernimmt die besten Vorschläge und behält nicht zugeordnete Positionen als Hinweis', () => {
    const draft = draftFrom(detail());

    expect(draft.customerId).toBe(3);
    expect(draft.customerReference).toBe('B-1');
    expect(draft.items).toEqual([
      { productId: 11, quantity: 4, hint: '200011 IKEA Beleuchtung' },
      { productId: null, quantity: 1, hint: 'Druckerpapier A4' }
    ]);
  });

  it('zeigt vom Absender nur den Namen', () => {
    expect(senderName('Greta Gärtner <greta@example.com>')).toBe('Greta Gärtner');
    expect(senderName('<greta@example.com>')).toBe('greta@example.com');
    expect(senderName(null)).toBe('–');
  });
});
