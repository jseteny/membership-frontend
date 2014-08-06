define([
    '$',
    'ajax',
    'stripe',
    'src/utils/Form',
    'config/stripeErrorMessages'
], function ($, ajax, stripe, Form, stripeErrorMessages) {

    ajax.init({page: {ajaxUrl: ''}});

    describe('Form module', function() {

        var EMPTY_STRING = '',
            creditCardNumbers = {
                visa: '4242424242424242',
                mastercard: '5555555555554444',
                american_express: '371449635398431',
                discover: '6011000990139424',
                diners_club: '38520000023237',
                jcb: '3566002020360505'
            },
            mockDataObj = {
                'tier' : 'Patron',
                'name.first' : 'Tintin',
                'name.last' : 'Last Name',
                'deliveryAddress.lineOne' : '',
                'deliveryAddress.lineTwo' : '',
                'deliveryAddress.town' : '',
                'deliveryAddress.countyOrState' : '',
                'deliveryAddress.postCode' : 'N1 9AG',
                'deliveryAddress.country' : 'United Kingdom',
                'billingAddress.lineOne' : '',
                'billingAddress.lineTwo' : '',
                'billingAddress.town' : '',
                'billingAddress.countyOrState' : '',
                'billingAddress.postCode' : '',
                'billingAddress.country' : 'United Kingdom',
                'payment.type' : 'annual'
            },
            stripeErrorObjects = {
                valid: {type: 'card_error', code: 'incorrect_number'},
                declinedCard: {type: 'card_error', code: 'card_declined', decline_code: 'card_not_supported'},
                invalid: {type: 'captain_haddock', code: 'tintin'}
            },
            submitButtonElement,
            errorMessageDisplayElement,
            creditCardImageElement,
            canonicalFormElement,
            formElement,
            form,
            submitButtonElement,
            errorMessageDisplayElement,
            creditCardImageElement,
            firstNameElement,
            lastNameElement,
            postcodeElement;

        // PhantomJS doesn't support bind yet
        Function.prototype.bind = Function.prototype.bind || function (context) {
            var fn = this;
            return function () {
                return fn.apply(context, arguments);
            };
        };

        beforeEach(function () {

            runs(function () {

                //pull this in once and cache it
                if (!form) {
                    ajax({
                        url: '/base/test/fixtures/paymentForm.fixture.html',
                        method: 'get',
                        success: function (resp) {
                            canonicalFormElement = $.create(resp)[0];
                        }
                    });
                }
            });

            waitsFor(function () {
                return !!canonicalFormElement;
            }, 'Fixture should be loaded', 1000);

            runs(function () {
                formElement = canonicalFormElement.cloneNode(true);
                form = new Form(formElement);
                submitButtonElement = $('.js-submit-input', formElement)[0];
                errorMessageDisplayElement = $('.js-payment-errors', formElement)[0];
                creditCardImageElement = $('.js-credit-card-image', formElement)[0];
                firstNameElement = $('.js-name-first', formElement)[0];
                lastNameElement = $('.js-name-last', formElement)[0];
                postcodeElement = $('.js-post-code', formElement)[0];
            });
        });

        afterEach(function () {
            formElement = null;
        });

        it('should add correct card type class to credit card image element', function() {
            var cardType, cardTypeClassname;

            for (cardType in creditCardNumbers) {

                form.displayCardTypeImage(creditCardNumbers[cardType]);
                cardTypeClassname = cardType.replace('_', '-');
                expect(creditCardImageElement.className).toContain(cardTypeClassname);
                expect(errorMessageDisplayElement.innerHTML).toEqual(EMPTY_STRING);
                expect(errorMessageDisplayElement.classList.contains('is-hidden')).toBeTruthy();
                expect(submitButtonElement.hasAttribute('disabled')).toBeFalsy();
            }
        });

        it('correct error returned from stripeErrorMessages via getErrorMessage', function() {
            var stripeErrorMessage = form.getErrorMessage(stripeErrorObjects.valid);

            expect(stripeErrorMessage).toEqual(stripeErrorMessages.card_error.incorrect_number);
        });

        it('undefined returned from stripeErrorMessages via getErrorMessage for invalid stripe error object', function() {
            var stripeErrorMessage = form.getErrorMessage(stripeErrorObjects.invalid);

            expect(stripeErrorMessage).toEqual(stripeErrorMessages.generic_error);
        });

        it('correct error returned from stripeErrorMessages via getErrorMessage for declined_card', function() {
            var stripeErrorMessage = form.getErrorMessage(stripeErrorObjects.declinedCard);

            expect(stripeErrorMessage).toEqual(stripeErrorMessages.card_error.card_declined.card_not_supported);
        });

        it('correct data object is built for serialization', function () {

            firstNameElement.value = mockDataObj['name.first'];
            lastNameElement.value = mockDataObj['name.last'];
            postcodeElement.value = mockDataObj['deliveryAddress.postCode'];

            var dataObj = form.buildDataObject(form.formElement);

            for (var prop in dataObj) {
                expect(dataObj[prop]).toEqual(mockDataObj[prop]);
            }
        });

        it('check non existing validator throws exception', function () {
            expect(form.checkValidatorExistsException.bind(this, 'randomValidator')).toThrow();
        });

        it('array is returned if non array sent in as argument', function () {
            var argsArray = form.createArgsArray('string');
            expect(argsArray).toBeTruthy(argsArray instanceof Array);
            expect(argsArray).toEqual(['string']);
        });

        it('array is returned if array sent in as argument', function () {
            var argsArray = form.createArgsArray(['string']);
            expect(argsArray).toBeTruthy(argsArray instanceof Array);
            expect(argsArray).toEqual(['string']);
        });

    });

});
