document.getElementById('dataForm').addEventListener('submit', function(event) {
    event.preventDefault(); // Zapobiega domyślnemu zachowaniu formularza

    // Zbierz dane z formularza
    const data = {
        name: document.getElementById('name').value,
        email: document.getElementById('email').value,
        item: {
            title: document.getElementById('title').value,
            author: document.getElementById('author').value,
            price: document.getElementById('price').value,
        },
        delivery: {
            company: document.getElementById('company').value,
            date: document.getElementById('date').value + ':00+00:00',
        },
        payment: {
            cardName: document.getElementById('cardName').value,
            validTo: document.getElementById('validTo').value,
            number: document.getElementById('number').value,
        }
    };

    // Wyślij dane do endpointu zamówienia książki
    fetch('http://localhost:8090/api/book/ordering', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        // .then(data => {
        //     console.log('Success:', data);
        //     document.getElementById('postResponse').textContent = JSON.stringify(data, null, 2);
        //     // Additional actions after successful order placement
        // })
        .catch(error => {
            console.error('Error:', error);
            document.getElementById('postResponse').textContent = 'Error: ' + error;
        });

    // Oddzielne zapytanie do pobierania danych do tabeli
    fetchPaymentData();
});

function fetchPaymentData() {
    fetch('http://localhost:8083/payment', {
        method: 'GET', // lub 'GET', w zależności od wymagań Twojego API
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ /* dane do wysłania, jeśli są wymagane */ })
    })
    .then(response => response.json())
    .then(data => {
        updateTable(data);
    })
    .catch(error => console.error('Error:', error));
}

function updateTable(data) {
    const table = document.getElementById('dataTable').getElementsByTagName('tbody')[0];
    table.innerHTML = '';

    // Zakładając, że 'data' jest tablicą obiektów
    data.forEach(item => {
        const newRow = table.insertRow();
        const newCell = newRow.insertCell(0);
        newCell.textContent = JSON.stringify(item, null, 2);
    });
}

// Możesz również automatycznie wczytywać dane do tabeli przy ładowaniu strony
document.addEventListener('DOMContentLoaded', function() {
    fetchPaymentData();
});
